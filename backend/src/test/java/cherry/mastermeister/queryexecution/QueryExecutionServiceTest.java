/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.queryexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.tools.Server;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.spring.JqwikSpringSupport;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.queryhistory.ExecutionRecord;
import cherry.mastermeister.queryhistory.QueryHistoryService;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.savedquery.SavedQuery;
import cherry.mastermeister.savedquery.SavedQueryRepository;
import cherry.mastermeister.savedquery.SavedQueryService;
import cherry.mastermeister.savedquery.Visibility;

/**
 * P6〜P8（business-logic-model.md）を検証するプロパティテスト。
 */
@JqwikSpringSupport
@DataJpaTest
class QueryExecutionServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";
    private static final long CONNECTION_ID = 1L;

    private static Server h2Server;
    private static Path h2BaseDir;
    private static ReadOnlySqlValidator validator;

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private final DialectStrategyFactory dialectStrategyFactory =
            new DialectStrategyFactory(List.of(new H2DialectStrategy()));

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @BeforeContainer
    static void startH2Server() throws SQLException, IOException {
        h2BaseDir = Files.createTempDirectory("query-execution-test");
        h2Server = Server.createTcpServer(
                "-tcpPort", "0", "-tcpAllowOthers", "-ifNotExists", "-baseDir", h2BaseDir.toString()).start();
        validator = new ReadOnlySqlValidator(10000, Duration.ofSeconds(5), 2);
    }

    @AfterContainer
    static void stopH2Server() throws IOException {
        if (validator != null) {
            validator.shutdown();
        }
        if (h2Server != null) {
            h2Server.stop();
        }
        if (h2BaseDir != null) {
            try (var paths = Files.walk(h2BaseDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    // P6: ページング有効時、返却行数は常にpageSize以下。
    @Property(tries = 10)
    void executeAdhocSqlWithPagingNeverExceedsPageSize(
            @ForAll("rowCounts") int rowCount, @ForAll("smallLimits") int pageSize
    ) throws Exception {
        String dbName = "QUERYEXECPG" + DB_COUNTER.incrementAndGet();
        seedRows(dbName, rowCount);
        QueryExecutionService service = newService(dbName, 1000);

        QueryResult result = service.executeAdhocSql(
                1L, CONNECTION_ID, "SELECT * FROM " + TEST_SCHEMA + ".T1", Map.of(),
                new PagingOption(true, 0, pageSize));

        assertThat(result.rows().size()).isLessThanOrEqualTo(pageSize);
        assertThat(result.truncated()).isFalse();
    }

    // P6: ページング無効時、返却行数は常にmax-result-rows以下であり、超過時に限りtruncated=true。
    @Property(tries = 10)
    void executeAdhocSqlWithoutPagingTruncatesAtMaxResultRowsBoundary(
            @ForAll("rowCounts") int rowCount, @ForAll("smallLimits") int maxResultRows
    ) throws Exception {
        String dbName = "QUERYEXECNP" + DB_COUNTER.incrementAndGet();
        seedRows(dbName, rowCount);
        QueryExecutionService service = newService(dbName, maxResultRows);

        QueryResult result = service.executeAdhocSql(
                1L, CONNECTION_ID, "SELECT * FROM " + TEST_SCHEMA + ".T1", Map.of(),
                new PagingOption(false, 0, 0));

        assertThat(result.rows().size()).isLessThanOrEqualTo(maxResultRows);
        assertThat(result.truncated()).isEqualTo(rowCount > maxResultRows);
    }

    // P7: 実行が成功するたびに、QueryHistoryとAuditLog（QUERY_EXECUTED）が常にそれぞれ1件だけ記録される。
    @Property(tries = 10)
    void executeAdhocSqlAlwaysRecordsHistoryAndAuditExactlyOnce(@ForAll("rowCounts") int rowCount) throws Exception {
        String dbName = "QUERYEXECP7" + DB_COUNTER.incrementAndGet();
        seedRows(dbName, rowCount);

        QueryHistoryService queryHistoryService = mock(QueryHistoryService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        QueryExecutionService service = newService(dbName, 1000, queryHistoryService, auditLogService);

        service.executeAdhocSql(1L, CONNECTION_ID, "SELECT * FROM " + TEST_SCHEMA + ".T1", Map.of(),
                new PagingOption(false, 0, 0));

        verify(queryHistoryService, times(1)).recordExecution(any());
        verify(auditLogService, times(1)).record(
                eq(EventCategory.DATA_ACCESS), eq(EventType.QUERY_EXECUTED), eq(1L), eq(CONNECTION_ID),
                eq(Result.SUCCESS), anyString(), anyString());
    }

    // P8: executeSavedQueryが成功するたびに、SavedQuery.executionCountは呼び出し前の値からちょうど
    //     1増加し、生成されるQueryHistory.executionCountはインクリメント後の値と常に一致する。
    @Property(tries = 10)
    void executeSavedQueryIncrementsExecutionCountConsistently(@ForAll("rowCounts") int rowCount) throws Exception {
        savedQueryRepository.deleteAll();
        String dbName = "QUERYEXECP8" + DB_COUNTER.incrementAndGet();
        seedRows(dbName, rowCount);

        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.save(new SavedQuery(
                1L, CONNECTION_ID, "q", "SELECT * FROM " + TEST_SCHEMA + ".T1",
                Visibility.PRIVATE, false, 0, now, now));
        int before = saved.getExecutionCount();

        QueryHistoryService queryHistoryService = mock(QueryHistoryService.class);
        QueryExecutionService service = newService(
                dbName, 1000, new SavedQueryService(savedQueryRepository), queryHistoryService,
                mock(AuditLogService.class));

        service.executeSavedQuery(1L, CONNECTION_ID, saved.getId(), Map.of(), new PagingOption(false, 0, 0));

        SavedQuery reloaded = savedQueryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getExecutionCount()).isEqualTo(before + 1);

        ArgumentCaptor<ExecutionRecord> captor = ArgumentCaptor.forClass(ExecutionRecord.class);
        verify(queryHistoryService).recordExecution(captor.capture());
        assertThat(captor.getValue().executionCount()).isEqualTo(reloaded.getExecutionCount());
    }

    @Provide
    Arbitrary<Integer> rowCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> smallLimits() {
        return Arbitraries.integers().between(1, 8);
    }

    private void seedRows(String dbName, int rowCount) throws SQLException {
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT PRIMARY KEY, VAL VARCHAR(50))");
            }
            for (int i = 0; i < rowCount; i++) {
                try (Statement st = setup.createStatement()) {
                    st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (" + i + ", 'v" + i + "')");
                }
            }
        }
    }

    private Connection openConnection(String dbName) throws SQLException {
        String url = "jdbc:h2:tcp://localhost:" + h2Server.getPort() + "/" + dbName;
        return DriverManager.getConnection(url, "sa", "sa");
    }

    private void createSchema(Connection conn, String schema) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        }
    }

    private QueryExecutionService newService(String dbName, int maxResultRows) {
        return newService(dbName, maxResultRows, mock(QueryHistoryService.class), mock(AuditLogService.class));
    }

    private QueryExecutionService newService(
            String dbName, int maxResultRows, QueryHistoryService queryHistoryService, AuditLogService auditLogService
    ) {
        return newService(
                dbName, maxResultRows, new SavedQueryService(savedQueryRepository), queryHistoryService,
                auditLogService);
    }

    private QueryExecutionService newService(
            String dbName, int maxResultRows, SavedQueryService savedQueryService,
            QueryHistoryService queryHistoryService, AuditLogService auditLogService
    ) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", h2Server.getPort(), dbName, "sa", "sa", null, now, now)));
        ConnectionPoolRegistry registry = new ConnectionPoolRegistry(
                connectionRepository, dialectStrategyFactory, 1, 0, Duration.ofSeconds(5));

        return new QueryExecutionService(
                savedQueryService, validator, new SqlParamDetector(), new PagingSqlBuilder(), connectionRepository,
                dialectStrategyFactory, registry, queryHistoryService, auditLogService,
                Duration.ofSeconds(30), maxResultRows);
    }

}