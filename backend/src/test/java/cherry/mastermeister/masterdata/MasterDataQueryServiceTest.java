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

package cherry.mastermeister.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.schema.ColumnDetail;
import cherry.mastermeister.schema.SchemaQueryService;
import cherry.mastermeister.schema.TableDetail;
import cherry.mastermeister.schema.TableType;

/**
 * P1・P2（business-logic-model.md）を検証するプロパティテスト。
 */
class MasterDataQueryServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";
    private static final List<String> GENERATED_COLUMNS = List.of("COL0", "COL1", "COL2", "COL3");

    private static Server h2Server;
    private static Path h2BaseDir;

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private final DialectStrategyFactory dialectStrategyFactory =
            new DialectStrategyFactory(List.of(new H2DialectStrategy()));

    @BeforeContainer
    static void startH2Server() throws SQLException, IOException {
        h2BaseDir = Files.createTempDirectory("master-data-query-test");
        h2Server = Server.createTcpServer(
                "-tcpPort", "0", "-tcpAllowOthers", "-ifNotExists", "-baseDir", h2BaseDir.toString()).start();
    }

    @AfterContainer
    static void stopH2Server() throws IOException {
        if (h2Server != null) {
            h2Server.stop();
        }
        if (h2BaseDir != null) {
            try (var paths = Files.walk(h2BaseDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    // P1: NONE権限のカラムはresult.columns()に一切含まれない。
    // P2: records.content()の各行の要素数はcolumns()の要素数と常に一致し、位置iの値は
    //     columns.get(i)のカラムに対応する（ID列は整数値、生成列は自身のカラム名文字列を
    //     格納することで、位置対応を直接検証できるようにしている）。
    @Property(tries = 20)
    void listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns(
            @ForAll("columnPermissionPatterns") List<Permission> generatedColumnPermissions,
            @ForAll("rowCounts") int rowCount
    ) throws Exception {
        String dbName = "MASTERDATAQUERY" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 "
                        + "(ID INT PRIMARY KEY, COL0 VARCHAR(50), COL1 VARCHAR(50), "
                        + "COL2 VARCHAR(50), COL3 VARCHAR(50))");
            }
            for (int i = 0; i < rowCount; i++) {
                try (Statement st = setup.createStatement()) {
                    st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES "
                            + "(" + i + ", 'COL0', 'COL1', 'COL2', 'COL3')");
                }
            }
        }

        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", Permission.READ);
        for (int i = 0; i < GENERATED_COLUMNS.size(); i++) {
            columnPermissions.put(GENERATED_COLUMNS.get(i), generatedColumnPermissions.get(i));
        }
        List<String> expectedSelectable = columnPermissions.entrySet().stream()
                .filter(entry -> entry.getValue() != Permission.NONE)
                .map(Map.Entry::getKey)
                .toList();

        MasterDataQueryService service = newService(dbName, columnPermissions);

        FilterCriteria criteria = new FilterCriteria(FilterMode.UI, List.of(), List.of(), null, null);
        RecordListResult result = service.listRecords(
                1L, 1L, TEST_SCHEMA, "T1", criteria, new PageRequest(0, 10));

        // P1: NONE権限のカラムが含まれないこと。
        assertThat(result.columns())
                .extracting(ColumnMetadata::columnName)
                .containsExactlyElementsOf(expectedSelectable);
        assertThat(result.columns())
                .noneMatch(column -> column.effectivePermission() == Permission.NONE);

        // P2: 各行の要素数がcolumns()の要素数と一致し、位置iの値がcolumns.get(i)に対応すること。
        assertThat(result.records().content()).hasSize(rowCount);
        for (List<Object> row : result.records().content()) {
            assertThat(row).hasSameSizeAs(result.columns());
            for (int i = 0; i < result.columns().size(); i++) {
                String columnName = result.columns().get(i).columnName();
                if ("ID".equals(columnName)) {
                    assertThat(row.get(i)).isInstanceOf(Number.class);
                } else {
                    assertThat(row.get(i)).isEqualTo(columnName);
                }
            }
        }
    }

    @Provide
    Arbitrary<List<Permission>> columnPermissionPatterns() {
        return Arbitraries.of(Permission.values()).list().ofSize(GENERATED_COLUMNS.size());
    }

    @Provide
    Arbitrary<Integer> rowCounts() {
        return Arbitraries.integers().between(0, 3);
    }

    private MasterDataQueryService newService(String dbName, Map<String, Permission> columnPermissions) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", h2Server.getPort(), dbName, "sa", "sa", null, now, now)));
        ConnectionPoolRegistry registry = new ConnectionPoolRegistry(
                connectionRepository, dialectStrategyFactory, 1, 0, Duration.ofSeconds(5));

        EffectivePermissionResolver permissionResolver = mock(EffectivePermissionResolver.class);
        when(permissionResolver.resolveEffectiveTablePermission(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Permission.READ);
        when(permissionResolver.resolveEffectiveColumnPermissions(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(columnPermissions);

        SchemaQueryService schemaQueryService = mock(SchemaQueryService.class);
        List<ColumnDetail> columns = new ArrayList<>();
        columns.add(new ColumnDetail("ID", "INTEGER", false, null, 1, 1));
        for (int i = 0; i < GENERATED_COLUMNS.size(); i++) {
            columns.add(new ColumnDetail(GENERATED_COLUMNS.get(i), "VARCHAR", true, null, i + 2, null));
        }
        when(schemaQueryService.getTableDetail(anyLong(), anyString(), anyString()))
                .thenReturn(new TableDetail(TEST_SCHEMA, "T1", TableType.TABLE, null, columns));

        return new MasterDataQueryService(
                schemaQueryService, permissionResolver, registry, connectionRepository, dialectStrategyFactory,
                mock(AuditLogService.class), Duration.ofSeconds(30), 100);
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

}