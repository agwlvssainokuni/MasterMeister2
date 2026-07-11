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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.common.exception.PermissionDeniedException;
import cherry.mastermeister.common.exception.ValidationException;
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
 * P6・P7・P8・P9・P10（business-logic-model.md）を検証するプロパティテスト。
 */
class MasterDataMutationServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";

    private static Server h2Server;
    private static Path h2BaseDir;

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private final DialectStrategyFactory dialectStrategyFactory =
            new DialectStrategyFactory(List.of(new H2DialectStrategy()));

    @BeforeContainer
    static void startH2Server() throws SQLException, IOException {
        h2BaseDir = Files.createTempDirectory("master-data-mutation-test");
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

    // P6: creates/updates/deletesのいずれか1件でも権限検証（canCreate/カラムUPDATE権限/canDelete）に
    //     失敗する場合、他の操作が全て有効であっても対象RDBMSの状態は呼び出し前後で一切変化しない。
    @Property(tries = 20)
    void applyChangesRejectsAllOrNothingWhenAnyOperationFailsPermission(
            @ForAll("failureTargets") int failureTarget
    ) throws Exception {
        boolean createFails = failureTarget == 0;
        boolean updateFails = failureTarget == 1;
        boolean deleteFails = failureTarget == 2;

        String dbName = "MASTERDATAMUTATIONP6" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT PRIMARY KEY, COL0 VARCHAR(50))");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (0, 'ORIG0')");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (2, 'ORIG2')");
            }
        }

        List<ColumnDetail> columns = List.of(
                new ColumnDetail("ID", "INTEGER", false, null, 1, 1),
                new ColumnDetail("COL0", "VARCHAR", true, null, 2, null));
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", Permission.UPDATE);
        columnPermissions.put("COL0", updateFails ? Permission.READ : Permission.UPDATE);

        AuditLogService auditLogService = mock(AuditLogService.class);
        MasterDataMutationService service = newService(
                dbName, columns, columnPermissions, !createFails, !deleteFails, auditLogService);

        MutationRequest request = new MutationRequest(
                List.of(new RecordCreate(Map.of("ID", 1, "COL0", "NEW"))),
                List.of(new RecordUpdate(Map.of("ID", 0), Map.of("COL0", "UPDATED"))),
                List.of(new RecordDelete(Map.of("ID", 2))));

        assertThatThrownBy(() -> service.applyChanges(1L, 1L, TEST_SCHEMA, "T1", request))
                .isInstanceOf(PermissionDeniedException.class);

        try (Connection check = openConnection(dbName);
                Statement st = check.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT ID, COL0 FROM " + TEST_SCHEMA + ".T1 ORDER BY ID")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("ID")).isEqualTo(0);
            assertThat(rs.getString("COL0")).isEqualTo("ORIG0");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("ID")).isEqualTo(2);
            assertThat(rs.getString("COL0")).isEqualTo("ORIG2");
            assertThat(rs.next()).isFalse();
        }
    }

    // P7: 主キーを持たないテーブルへのRecordUpdateが1件でも含まれる場合、対象カラムの権限値
    //     によらずリクエスト全体が拒否される。
    @Property(tries = 20)
    void applyChangesRejectsRecordUpdateOnTableWithoutPrimaryKey(
            @ForAll("columnPermissionPatterns") List<Permission> generatedColumnPermissions
    ) throws Exception {
        String dbName = "MASTERDATAMUTATIONP7" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT, COL0 VARCHAR(50))");
            }
        }

        List<ColumnDetail> columns = List.of(
                new ColumnDetail("ID", "INTEGER", false, null, 1, null),
                new ColumnDetail("COL0", "VARCHAR", true, null, 2, null));
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", generatedColumnPermissions.get(0));
        columnPermissions.put("COL0", generatedColumnPermissions.get(1));

        AuditLogService auditLogService = mock(AuditLogService.class);
        MasterDataMutationService service = newService(
                dbName, columns, columnPermissions, true, true, auditLogService);

        MutationRequest request = new MutationRequest(
                List.of(), List.of(new RecordUpdate(Map.of("ID", 0), Map.of("COL0", "UPDATED"))), List.of());

        assertThatThrownBy(() -> service.applyChanges(1L, 1L, TEST_SCHEMA, "T1", request))
                .isInstanceOf(ValidationException.class);
    }

    // P8: 主キーを持たないテーブルへのRecordDeleteは、補助権限Dの値（canDeleteの解決結果）に
    //     よらず常に拒否される（canDeleteが常にfalseになるU4既存仕様に起因する連携動作）。
    @Property(tries = 20)
    void applyChangesRejectsRecordDeleteOnTableWithoutPrimaryKey(
            @ForAll("columnPermissionPatterns") List<Permission> generatedColumnPermissions
    ) throws Exception {
        String dbName = "MASTERDATAMUTATIONP8" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT, COL0 VARCHAR(50))");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (0, 'ORIG0')");
            }
        }

        List<ColumnDetail> columns = List.of(
                new ColumnDetail("ID", "INTEGER", false, null, 1, null),
                new ColumnDetail("COL0", "VARCHAR", true, null, 2, null));
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", generatedColumnPermissions.get(0));
        columnPermissions.put("COL0", generatedColumnPermissions.get(1));

        AuditLogService auditLogService = mock(AuditLogService.class);
        // 主キーなしテーブルではEffectivePermissionResolver.canDeleteは常にfalseを返す
        // （U4既存仕様）ため、その契約どおりfalseを注入する。
        MasterDataMutationService service = newService(
                dbName, columns, columnPermissions, true, false, auditLogService);

        MutationRequest request = new MutationRequest(
                List.of(), List.of(), List.of(new RecordDelete(Map.of("ID", 0))));

        assertThatThrownBy(() -> service.applyChanges(1L, 1L, TEST_SCHEMA, "T1", request))
                .isInstanceOf(PermissionDeniedException.class);

        try (Connection check = openConnection(dbName);
                Statement st = check.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) AS CNT FROM " + TEST_SCHEMA + ".T1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("CNT")).isEqualTo(1);
        }
    }

    // P9: applyChanges実行中にSQLExceptionが発生した場合、対象RDBMSの状態は呼び出し前と
    //     完全に一致する（トランザクション原子性。他の操作が事前に実行済みであっても全てロールバックされる）。
    @Property(tries = 20)
    void applyChangesRollsBackAllChangesWhenSqlExceptionOccurs(
            @ForAll("failureTargets") int failureTarget
    ) throws Exception {
        boolean createFails = failureTarget == 0;
        boolean updateFails = failureTarget == 1;
        boolean deleteFails = failureTarget == 2;

        String dbName = "MASTERDATAMUTATIONP9" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT PRIMARY KEY, COL0 VARCHAR(5) NOT NULL)");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (0, 'A')");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (2, 'B')");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (3, 'C')");
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T2 (ID INT PRIMARY KEY, PARENT_ID INT, "
                        + "FOREIGN KEY (PARENT_ID) REFERENCES " + TEST_SCHEMA + ".T1(ID))");
                st.execute("INSERT INTO " + TEST_SCHEMA + ".T2 VALUES (100, 2)");
            }
        }

        List<ColumnDetail> columns = List.of(
                new ColumnDetail("ID", "INTEGER", false, null, 1, 1),
                new ColumnDetail("COL0", "VARCHAR", false, null, 2, null));
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", Permission.UPDATE);
        columnPermissions.put("COL0", Permission.UPDATE);

        AuditLogService auditLogService = mock(AuditLogService.class);
        MasterDataMutationService service = newService(
                dbName, columns, columnPermissions, true, true, auditLogService);

        // createFails: 既存ID(0)と重複するPRIMARY KEY違反を発生させる。
        // updateFails: VARCHAR(5)を超える値を設定し、値超過エラーを発生させる。
        // deleteFails: T2から参照中の行(ID=2)を削除し、外部キー制約違反を発生させる。
        int createId = createFails ? 0 : 1;
        String updateValue = updateFails ? "TOOLONG" : "UPD";
        int deleteId = deleteFails ? 2 : 3;

        MutationRequest request = new MutationRequest(
                List.of(new RecordCreate(Map.of("ID", createId, "COL0", "NEW"))),
                List.of(new RecordUpdate(Map.of("ID", 0), Map.of("COL0", updateValue))),
                List.of(new RecordDelete(Map.of("ID", deleteId))));

        MutationResult result = service.applyChanges(1L, 1L, TEST_SCHEMA, "T1", request);

        assertThat(result.success()).isFalse();

        try (Connection check = openConnection(dbName);
                Statement st = check.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT ID, COL0 FROM " + TEST_SCHEMA + ".T1 ORDER BY ID")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("ID")).isEqualTo(0);
            assertThat(rs.getString("COL0")).isEqualTo("A");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("ID")).isEqualTo(2);
            assertThat(rs.getString("COL0")).isEqualTo("B");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("ID")).isEqualTo(3);
            assertThat(rs.getString("COL0")).isEqualTo("C");
            assertThat(rs.next()).isFalse();
        }
        try (Connection check = openConnection(dbName);
                Statement st = check.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) AS CNT FROM " + TEST_SCHEMA + ".T2")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("CNT")).isEqualTo(1);
        }
    }

    // P10: 成功時、creates/updates/deletesの内容が過不足なくRDBMSに反映される。
    @Property(tries = 20)
    void applyChangesReflectsCreatesUpdatesDeletesExactlyOnSuccess(
            @ForAll @IntRange(min = 0, max = 2) int createCount,
            @ForAll @IntRange(min = 0, max = 2) int updateCount,
            @ForAll @IntRange(min = 0, max = 2) int deleteCount
    ) throws Exception {
        String dbName = "MASTERDATAMUTATIONP10" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 (ID INT PRIMARY KEY, COL0 VARCHAR(50))");
                for (int i = 0; i < 5; i++) {
                    st.execute("INSERT INTO " + TEST_SCHEMA + ".T1 VALUES (" + i + ", 'V" + i + "')");
                }
            }
        }

        List<ColumnDetail> columns = List.of(
                new ColumnDetail("ID", "INTEGER", false, null, 1, 1),
                new ColumnDetail("COL0", "VARCHAR", true, null, 2, null));
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("ID", Permission.UPDATE);
        columnPermissions.put("COL0", Permission.UPDATE);

        AuditLogService auditLogService = mock(AuditLogService.class);
        MasterDataMutationService service = newService(
                dbName, columns, columnPermissions, true, true, auditLogService);

        // updates は ID 0..updateCount-1、deletes はその直後の ID を対象とし、互いに重複しない。
        List<RecordCreate> creates = new ArrayList<>();
        for (int i = 0; i < createCount; i++) {
            creates.add(new RecordCreate(Map.of("ID", 100 + i, "COL0", "NEW" + i)));
        }
        List<RecordUpdate> updates = new ArrayList<>();
        for (int i = 0; i < updateCount; i++) {
            updates.add(new RecordUpdate(Map.of("ID", i), Map.of("COL0", "UPD" + i)));
        }
        List<RecordDelete> deletes = new ArrayList<>();
        for (int i = updateCount; i < updateCount + deleteCount; i++) {
            deletes.add(new RecordDelete(Map.of("ID", i)));
        }

        MutationRequest request = new MutationRequest(creates, updates, deletes);

        MutationResult result = service.applyChanges(1L, 1L, TEST_SCHEMA, "T1", request);

        assertThat(result.success()).isTrue();
        assertThat(result.createdCount()).isEqualTo(createCount);
        assertThat(result.updatedCount()).isEqualTo(updateCount);
        assertThat(result.deletedCount()).isEqualTo(deleteCount);

        try (Connection check = openConnection(dbName)) {
            for (int i = 0; i < createCount; i++) {
                try (Statement st = check.createStatement();
                        ResultSet rs = st.executeQuery(
                                "SELECT COL0 FROM " + TEST_SCHEMA + ".T1 WHERE ID = " + (100 + i))) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("COL0")).isEqualTo("NEW" + i);
                }
            }
            for (int i = 0; i < updateCount; i++) {
                try (Statement st = check.createStatement();
                        ResultSet rs = st.executeQuery(
                                "SELECT COL0 FROM " + TEST_SCHEMA + ".T1 WHERE ID = " + i)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("COL0")).isEqualTo("UPD" + i);
                }
            }
            for (int i = updateCount; i < updateCount + deleteCount; i++) {
                try (Statement st = check.createStatement();
                        ResultSet rs = st.executeQuery(
                                "SELECT COUNT(*) AS CNT FROM " + TEST_SCHEMA + ".T1 WHERE ID = " + i)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("CNT")).isEqualTo(0);
                }
            }
            for (int i = updateCount + deleteCount; i < 5; i++) {
                try (Statement st = check.createStatement();
                        ResultSet rs = st.executeQuery(
                                "SELECT COL0 FROM " + TEST_SCHEMA + ".T1 WHERE ID = " + i)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("COL0")).isEqualTo("V" + i);
                }
            }
            try (Statement st = check.createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) AS CNT FROM " + TEST_SCHEMA + ".T1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("CNT")).isEqualTo(5 + createCount - deleteCount);
            }
        }
    }

    @Provide
    Arbitrary<Integer> failureTargets() {
        return Arbitraries.integers().between(0, 2);
    }

    @Provide
    Arbitrary<List<Permission>> columnPermissionPatterns() {
        return Arbitraries.of(Permission.values()).list().ofSize(2);
    }

    private MasterDataMutationService newService(
            String dbName, List<ColumnDetail> columns, Map<String, Permission> columnPermissions,
            boolean canCreate, boolean canDelete, AuditLogService auditLogService
    ) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", h2Server.getPort(), dbName, "sa", "sa", null, now, now)));
        ConnectionPoolRegistry registry = new ConnectionPoolRegistry(
                connectionRepository, dialectStrategyFactory, 1, 0, Duration.ofSeconds(5));

        EffectivePermissionResolver permissionResolver = mock(EffectivePermissionResolver.class);
        when(permissionResolver.canCreate(anyLong(), anyLong(), anyString(), anyString())).thenReturn(canCreate);
        when(permissionResolver.canDelete(anyLong(), anyLong(), anyString(), anyString())).thenReturn(canDelete);
        when(permissionResolver.resolveEffectiveColumnPermissions(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(columnPermissions);

        SchemaQueryService schemaQueryService = mock(SchemaQueryService.class);
        when(schemaQueryService.getTableDetail(anyLong(), anyString(), anyString()))
                .thenReturn(new TableDetail(TEST_SCHEMA, "T1", TableType.TABLE, null, columns));

        return new MasterDataMutationService(
                schemaQueryService, permissionResolver, registry, connectionRepository, dialectStrategyFactory,
                auditLogService, Duration.ofSeconds(30), 500);
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