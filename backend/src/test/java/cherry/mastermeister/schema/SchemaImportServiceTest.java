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

package cherry.mastermeister.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.spring.JqwikSpringSupport;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

/**
 * P7・P8・P9・P10（business-logic-model.md）を検証するプロパティテスト。
 */
class SchemaImportServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";

    private static Server h2Server;
    private static Path h2BaseDir;

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();
    private static final AtomicLong TABLE_ID_SEQ = new AtomicLong();
    private static final AtomicLong COLUMN_ID_SEQ = new AtomicLong();

    private final DialectStrategyFactory dialectStrategyFactory =
            new DialectStrategyFactory(List.of(new H2DialectStrategy()));

    // ファイルベースDBを使うのは、SchemaImportServiceがconnection.getDatabaseName()を
    // そのままJDBCカタログ名として扱うため（本番同様、接続URLのDB名とカタログ名を一致させる必要がある）。
    // mem:プレフィクス方式だとJDBC URL上のDB名とH2が報告するカタログ名が一致せず、
    // resolveSchemaNames/getTablesが常に0件になる。
    @BeforeContainer
    static void startH2Server() throws SQLException, IOException {
        h2BaseDir = Files.createTempDirectory("schema-import-test");
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

    private enum ChangePattern { UNCHANGED, ADDED, DELETED }

    // P7（idの不変性）・P8（stale=trueとなり行削除されない）・P9（連続実行のIdempotence）を
    // テーブル単位の変化パターン（不変/追加/削除）で検証する。
    @Property(tries = 15)
    void importSchemaHandlesTableChangePatterns(@ForAll("changePatterns") ChangePattern pattern) throws Exception {
        String dbName = "SCHEMAIMPORT" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            createTable(setup, TEST_SCHEMA, "T1");
            createTable(setup, TEST_SCHEMA, "T2");
        }

        FakeRepositories repos = new FakeRepositories();
        SchemaImportService service = newService(dbName, repos);

        assertThat(service.importSchema(1L, 1L).success()).isTrue();
        Map<String, Long> idsAfterFirst = testSchemaTableIds(repos);
        List<TableSnapshot> snapshotAfterFirst = testSchemaTableSnapshots(repos);

        try (Connection change = openConnection(dbName)) {
            switch (pattern) {
                case UNCHANGED -> { }
                case ADDED -> createTable(change, TEST_SCHEMA, "T3");
                case DELETED -> dropTable(change, TEST_SCHEMA, "T2");
            }
        }

        assertThat(service.importSchema(1L, 1L).success()).isTrue();
        Map<String, Long> idsAfterSecond = testSchemaTableIds(repos);

        // P7: 既存物理名のidは呼び出し前後で不変。
        for (Map.Entry<String, Long> entry : idsAfterFirst.entrySet()) {
            assertThat(idsAfterSecond).containsEntry(entry.getKey(), entry.getValue());
        }

        switch (pattern) {
            case UNCHANGED -> {
                // P9: stale=false集合（id・属性含む、タイムスタンプ除く）は1回目実行時と完全一致。
                assertThat(testSchemaTableSnapshots(repos)).containsExactlyInAnyOrderElementsOf(snapshotAfterFirst);
            }
            case ADDED -> {
                assertThat(idsAfterSecond).containsKey("T3");
                assertThat(testSchemaTable(repos, "T3").isStale()).isFalse();
            }
            case DELETED -> {
                // P8: 削除された物理名はstale=trueとなり、行自体は削除されない。
                SchemaTable t2 = testSchemaTable(repos, "T2");
                assertThat(t2).isNotNull();
                assertThat(t2.isStale()).isTrue();
                assertThat(t2.getId()).isEqualTo(idsAfterFirst.get("T2"));
                assertThat(testSchemaTable(repos, "T1").isStale()).isFalse();
            }
        }
    }

    // P7・P8・P9をカラム単位の変化パターン（不変/追加/削除）で検証する。
    @Property(tries = 15)
    void importSchemaHandlesColumnChangePatterns(@ForAll("changePatterns") ChangePattern pattern) throws Exception {
        String dbName = "SCHEMAIMPORT" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".T1 "
                        + "(ID INT PRIMARY KEY, NAME VARCHAR(50), NOTE VARCHAR(50))");
            }
        }

        FakeRepositories repos = new FakeRepositories();
        SchemaImportService service = newService(dbName, repos);

        assertThat(service.importSchema(1L, 1L).success()).isTrue();
        Map<String, Long> idsAfterFirst = testTableColumnIds(repos, "T1");

        try (Connection change = openConnection(dbName)) {
            try (Statement st = change.createStatement()) {
                switch (pattern) {
                    case UNCHANGED -> { }
                    case ADDED -> st.execute("ALTER TABLE " + TEST_SCHEMA + ".T1 ADD EXTRA VARCHAR(50)");
                    case DELETED -> st.execute("ALTER TABLE " + TEST_SCHEMA + ".T1 DROP COLUMN NOTE");
                }
            }
        }

        assertThat(service.importSchema(1L, 1L).success()).isTrue();
        Map<String, Long> idsAfterSecond = testTableColumnIds(repos, "T1");

        // P7: 既存物理名のidは呼び出し前後で不変。
        for (Map.Entry<String, Long> entry : idsAfterFirst.entrySet()) {
            assertThat(idsAfterSecond).containsEntry(entry.getKey(), entry.getValue());
        }

        switch (pattern) {
            case UNCHANGED -> assertThat(idsAfterSecond).containsExactlyInAnyOrderEntriesOf(idsAfterFirst);
            case ADDED -> {
                assertThat(idsAfterSecond).containsKey("EXTRA");
                assertThat(testColumn(repos, idsAfterSecond.get("EXTRA")).isStale()).isFalse();
            }
            case DELETED -> {
                // P8: 削除された物理名はstale=trueとなり、行自体は削除されない。
                SchemaColumn note = testColumn(repos, idsAfterFirst.get("NOTE"));
                assertThat(note).isNotNull();
                assertThat(note.isStale()).isTrue();
                assertThat(testColumn(repos, idsAfterFirst.get("NAME")).isStale()).isFalse();
            }
        }
    }

    @Provide
    Arbitrary<ChangePattern> changePatterns() {
        return Arbitraries.of(ChangePattern.values());
    }

    // P11: ビュー取り込み時、SchemaColumn.primaryKeySequenceは常にnullである。
    // importColumns()はtableType == TABLEの場合のみDatabaseMetaData.getPrimaryKeysを問い合わせ、
    // VIEWの場合はprimaryKeySequencesが常に空のMapのまま（=lookupは常にnullを返す）実装のため、
    // 基底テーブル側でPRIMARY KEYとして定義されている列をビューがそのまま射影していても、
    // ビュー側のSchemaColumnとしてはprimaryKeySequenceがnullになることを検証する。
    @Property(tries = 15)
    void importSchemaSetsNullPrimaryKeySequenceForViewColumns(@ForAll("viewColumnCounts") int columnCount) throws Exception {
        String dbName = "SCHEMAIMPORT" + DB_COUNTER.incrementAndGet();
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, TEST_SCHEMA);
            try (Statement st = setup.createStatement()) {
                StringBuilder extraCols = new StringBuilder();
                for (int i = 0; i < columnCount; i++) {
                    extraCols.append(", C").append(i).append(" INT");
                }
                st.execute("CREATE TABLE " + TEST_SCHEMA + ".BASE (ID INT PRIMARY KEY" + extraCols + ")");
                st.execute("CREATE VIEW " + TEST_SCHEMA + ".V1 AS SELECT * FROM " + TEST_SCHEMA + ".BASE");
            }
        }

        FakeRepositories repos = new FakeRepositories();
        SchemaImportService service = newService(dbName, repos);

        assertThat(service.importSchema(1L, 1L).success()).isTrue();

        SchemaTable view = testSchemaTable(repos, "V1");
        assertThat(view).isNotNull();
        assertThat(view.getTableType()).isEqualTo(TableType.VIEW);

        Map<String, Long> viewColumnIds = testTableColumnIds(repos, "V1");
        assertThat(viewColumnIds).hasSize(columnCount + 1).containsKey("ID");
        for (Long columnId : viewColumnIds.values()) {
            assertThat(testColumn(repos, columnId).getPrimaryKeySequence()).isNull();
        }
    }

    @Provide
    Arbitrary<Integer> viewColumnCounts() {
        return Arbitraries.integers().between(0, 3);
    }

    // P10（importSchema失敗時のロールバックRound-trip）は、実JPAリポジトリ・実トランザクション境界
    // でのみ検証可能なため、@DataJpaTest（内部DB用の埋め込みH2）＋@JqwikSpringSupportで
    // Spring TestContextをjqwikのプロパティテストに適用するグループとして分離する。
    // テストメソッド自体を包む外側トランザクション（@DataJpaTestが既定で付与する）を
    // Propagation.NOT_SUPPORTEDで無効化し、importSchema()自身の@Transactional
    // （Propagation.REQUIRED）がテストメソッドから見て新規かつ唯一の物理トランザクションとなる
    // ようにしている。これにより、失敗時のロールバックがimportSchema()の呼び出しが返る時点で
    // 実際に完了しており、呼び出し後にリポジトリで内部DB状態を検証すれば
    // 「ロールバック済みの実データ」を確認できる（テストの外側トランザクション内で検証すると、
    // ロールバックがテスト終了時まで遅延され、コミット前の変更がまだ見えてしまうため）。
    @Group
    @DataJpaTest
    @JqwikSpringSupport
    class RollbackRoundTrip {

        @Autowired
        RdbmsConnectionRepository connectionRepository;
        @Autowired
        SchemaTableRepository tableRepository;
        @Autowired
        SchemaColumnRepository columnRepository;
        @Autowired
        SchemaImportService service;

        // SchemaImportServiceを`new`で直接生成すると、Springのトランザクション用AOPプロキシを
        // 経由しないため@Transactionalが一切効かず（=物理トランザクションの開始/ロールバックが
        // 起きず）P10の検証ができない。そのためSpringコンテナ管理Beanとして登録し、
        // @Autowiredでプロキシ経由のインスタンスを取得する。カラム保存を確実に失敗させるため、
        // SchemaColumnRepositoryをJDK動的プロキシで包み、save呼び出し時のみ例外を送出し、
        // 他の呼び出しは実Beanへ委譲する（Mockito.spy()はSpring DataのJPAリポジトリ実装が
        // JDK動的プロキシであるためUnfinishedStubbingExceptionを引き起こし利用不可だった）。
        @TestConfiguration
        static class ImportServiceUnderTestConfig {
            @Bean
            SchemaImportService schemaImportServiceUnderTest(
                    RdbmsConnectionRepository rdbmsConnectionRepository,
                    SchemaTableRepository schemaTableRepository,
                    SchemaColumnRepository schemaColumnRepository) {
                DialectStrategyFactory factory = new DialectStrategyFactory(List.of(new H2DialectStrategy()));
                ConnectionPoolRegistry registry = new ConnectionPoolRegistry(
                        rdbmsConnectionRepository, factory, 1, 0, Duration.ofSeconds(5));
                SchemaColumnRepository failingColumnRepository = (SchemaColumnRepository) Proxy.newProxyInstance(
                        SchemaColumnRepository.class.getClassLoader(),
                        new Class<?>[]{SchemaColumnRepository.class},
                        (proxy, method, args) -> {
                            if ("save".equals(method.getName())) {
                                throw new RuntimeException("injected failure for P10 rollback test");
                            }
                            return method.invoke(schemaColumnRepository, args);
                        });
                return new SchemaImportService(
                        rdbmsConnectionRepository, schemaTableRepository, failingColumnRepository,
                        registry, factory, mock(AuditLogService.class), mock(ApplicationEventPublisher.class));
            }
        }

        // P10: 取り込み処理途中（1テーブル目の物理名upsert後・カラム保存中）で想定外の実行時例外が
        // 発生した場合、importSchemaの@Transactionalロールバックにより内部DB状態
        // （SchemaTable/SchemaColumn）が呼び出し前と完全に一致すること（新規行が一切残らないこと）
        // を検証する。テーブル数はtableCountで1〜3をjqwikで生成し、少なくとも1テーブル分の
        // 物理名upsertがカラム保存失敗より前に成功していることを保証する。
        // テストメソッド自体を包む外側トランザクション（@DataJpaTestが既定で付与する）を
        // Propagation.NOT_SUPPORTEDで無効化し、importSchema()自身の@Transactional
        // （Propagation.REQUIRED）がテストメソッドから見て新規かつ唯一の物理トランザクションとなる
        // ようにしている。これにより、失敗時のロールバックがimportSchema()の呼び出しが返る時点で
        // 実際に完了しており、呼び出し後にリポジトリで内部DB状態を検証すれば
        // 「ロールバック済みの実データ」を確認できる（テストの外側トランザクション内で検証すると、
        // ロールバックがテスト終了時まで遅延され、コミット前の変更がまだ見えてしまうため）。
        @Property(tries = 10)
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void importSchemaRollsBackAllChangesOnFailure(@ForAll("tableCounts") int tableCount) throws Exception {
            String dbName = "SCHEMAIMPORT" + DB_COUNTER.incrementAndGet();
            try (Connection setup = openConnection(dbName)) {
                createSchema(setup, TEST_SCHEMA);
                for (int i = 0; i < tableCount; i++) {
                    createTable(setup, TEST_SCHEMA, "T" + i);
                }
            }

            Instant now = Instant.now();
            RdbmsConnection connection = connectionRepository.save(new RdbmsConnection(
                    "test", RdbmsType.H2, "localhost", h2Server.getPort(), dbName, "sa", "sa", null, now, now));
            Long connectionId = connection.getId();

            assertThatThrownBy(() -> service.importSchema(connectionId, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("injected failure for P10 rollback test");

            assertThat(tableRepository.findByConnectionId(connectionId)).isEmpty();
            assertThat(columnRepository.findAll()).isEmpty();
        }

        @Provide
        Arbitrary<Integer> tableCounts() {
            return Arbitraries.integers().between(1, 3);
        }
    }

    private SchemaImportService newService(String dbName, FakeRepositories repos) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", h2Server.getPort(), dbName, "sa", "sa", null, now, now)));
        ConnectionPoolRegistry registry = new ConnectionPoolRegistry(
                connectionRepository, dialectStrategyFactory, 1, 0, Duration.ofSeconds(5));
        return new SchemaImportService(
                connectionRepository, repos.tableRepository(), repos.columnRepository(),
                registry, dialectStrategyFactory, mock(AuditLogService.class), mock(ApplicationEventPublisher.class));
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

    private void createTable(Connection conn, String schema, String table) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + schema + "." + table + " (ID INT PRIMARY KEY, NAME VARCHAR(50))");
        }
    }

    private void dropTable(Connection conn, String schema, String table) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE " + schema + "." + table);
        }
    }

    private Map<String, Long> testSchemaTableIds(FakeRepositories repos) {
        return repos.tables.stream()
                .filter(t -> TEST_SCHEMA.equals(t.getSchemaName()))
                .collect(Collectors.toMap(SchemaTable::getTableName, SchemaTable::getId));
    }

    private SchemaTable testSchemaTable(FakeRepositories repos, String tableName) {
        return repos.tables.stream()
                .filter(t -> TEST_SCHEMA.equals(t.getSchemaName()) && tableName.equals(t.getTableName()))
                .findFirst().orElse(null);
    }

    private List<TableSnapshot> testSchemaTableSnapshots(FakeRepositories repos) {
        return repos.tables.stream()
                .filter(t -> TEST_SCHEMA.equals(t.getSchemaName()) && !t.isStale())
                .map(TableSnapshot::of)
                .toList();
    }

    private Map<String, Long> testTableColumnIds(FakeRepositories repos, String tableName) {
        Long tableId = testSchemaTableIds(repos).get(tableName);
        return repos.columns.stream()
                .filter(c -> tableId.equals(c.getTableId()))
                .collect(Collectors.toMap(SchemaColumn::getColumnName, SchemaColumn::getId));
    }

    private SchemaColumn testColumn(FakeRepositories repos, Long columnId) {
        return repos.columns.stream()
                .filter(c -> c.getId().equals(columnId))
                .findFirst().orElse(null);
    }

    private static void assignId(Object entity, long id) throws NoSuchFieldException, IllegalAccessException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private record TableSnapshot(
            Long id, Long connectionId, String schemaName, String tableName, TableType tableType, String comment
    ) {
        static TableSnapshot of(SchemaTable table) {
            return new TableSnapshot(table.getId(), table.getConnectionId(), table.getSchemaName(),
                    table.getTableName(), table.getTableType(), table.getComment());
        }
    }

    private static final class FakeRepositories {
        final List<SchemaTable> tables = new ArrayList<>();
        final List<SchemaColumn> columns = new ArrayList<>();

        SchemaTableRepository tableRepository() {
            SchemaTableRepository repo = mock(SchemaTableRepository.class);
            when(repo.findByConnectionIdAndSchemaNameAndTableName(anyLong(), anyString(), anyString()))
                    .thenAnswer(inv -> tables.stream()
                            .filter(t -> t.getConnectionId().equals(inv.getArgument(0))
                                    && t.getSchemaName().equals(inv.getArgument(1))
                                    && t.getTableName().equals(inv.getArgument(2)))
                            .findFirst());
            when(repo.save(any())).thenAnswer(inv -> {
                SchemaTable table = inv.getArgument(0);
                assignId(table, TABLE_ID_SEQ.incrementAndGet());
                tables.add(table);
                return table;
            });
            when(repo.findByConnectionId(anyLong()))
                    .thenAnswer(inv -> tables.stream()
                            .filter(t -> t.getConnectionId().equals(inv.getArgument(0)))
                            .toList());
            return repo;
        }

        SchemaColumnRepository columnRepository() {
            SchemaColumnRepository repo = mock(SchemaColumnRepository.class);
            when(repo.findByTableIdAndColumnName(anyLong(), anyString()))
                    .thenAnswer(inv -> columns.stream()
                            .filter(c -> c.getTableId().equals(inv.getArgument(0))
                                    && c.getColumnName().equals(inv.getArgument(1)))
                            .findFirst());
            when(repo.save(any())).thenAnswer(inv -> {
                SchemaColumn column = inv.getArgument(0);
                assignId(column, COLUMN_ID_SEQ.incrementAndGet());
                columns.add(column);
                return column;
            });
            when(repo.findByTableId(anyLong()))
                    .thenAnswer(inv -> columns.stream()
                            .filter(c -> c.getTableId().equals(inv.getArgument(0)))
                            .toList());
            return repo;
        }
    }

}