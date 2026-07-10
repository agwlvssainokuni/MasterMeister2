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

package cherry.mastermeister.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.spring.JqwikSpringSupport;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.schema.SchemaImportService;
import cherry.mastermeister.userregistration.Role;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;
import cherry.mastermeister.userregistration.UserStatus;

/**
 * P11（business-logic-model.md）を検証するプロパティテスト。
 * U3{@code SchemaImportService.importSchema}の再取り込みが{@code PermissionAssignment}/
 * {@code AuxPermissionAssignment}側の行を一切変更しなくても、{@code SchemaReimportedEvent}
 * 発行〜{@link PermissionCacheInvalidationListener#onSchemaReimported}〜
 * {@code EffectivePermissionResolver}再判定の連携により、直後の{@code canCreate}/
 * {@code resolveEffectiveColumnPermissions}が必ず変更後のスキーマ構造を反映することを検証する。
 */
@SpringBootTest
@JqwikSpringSupport
class SchemaReimportCacheConsistencyTest {

    private static final Long ADMIN_ID = 1L;
    private static final String SCHEMA = "TESTSCHEMA";
    private static final String TABLE = "T1";

    // SchemaImportServiceTestと同じ理由（connection.getDatabaseName()がJDBCカタログ名として
    // 使われるため）でH2 TCPサーバ上のファイルベースDBを使う。各プロパティ試行ごとに
    // 専用のDBを新規作成するため、試行間のデータ競合は発生しない。
    private static Server h2Server;
    private static Path h2BaseDir;

    private static final long RUN_SEED = System.nanoTime();
    private static final AtomicLong SEQ = new AtomicLong();

    @BeforeContainer
    static void startH2Server() throws SQLException, IOException {
        h2BaseDir = Files.createTempDirectory("schema-reimport-cache-test");
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

    @Autowired
    private SchemaImportService schemaImportService;

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionAssignmentService permissionAssignmentService;

    @Autowired
    private EffectivePermissionResolver resolver;

    // 主キー構成の変化（なし→あり、あり→なし）がcanCreateを反転させることを、
    // PermissionAssignment/AuxPermissionAssignmentを一切変更せずに検証する
    // （2.5手順5: 主キーなしテーブルは補助権限Cのみで作成許可、主キーありテーブルは
    // 主キー全カラムがUPDATE以上必要。ここでは主キー列に主権限を一切付与しないため、
    // 主キーへの遷移直後はcanCreateがfalseへ反転するはずである）。
    @Property(tries = 10)
    void primaryKeyRestructuringChangesCanCreateWithoutPermissionAssignmentChange(
            @ForAll boolean startsWithPrimaryKey
    ) throws Exception {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        String dbName = "P11PK" + seq;
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, SCHEMA);
            createSingleColumnTable(setup, TABLE, startsWithPrimaryKey);
        }

        Long connectionId = registerConnection(dbName, seq);
        Long userId = createUser("p11-pk-user-" + seq + "@example.com");
        PrincipalRef principal = new PrincipalRef(PrincipalType.USER, userId);

        assertThat(schemaImportService.importSchema(connectionId, ADMIN_ID).success()).isTrue();
        permissionAssignmentService.setAuxPermission(
                ADMIN_ID, principal, connectionId, SCHEMA, Optional.of(TABLE), AuxPermissionType.CREATE, true);

        assertThat(resolver.canCreate(userId, connectionId, SCHEMA, TABLE)).isEqualTo(!startsWithPrimaryKey);

        try (Connection change = openConnection(dbName)) {
            dropTable(change, TABLE);
            createSingleColumnTable(change, TABLE, !startsWithPrimaryKey);
        }
        assertThat(schemaImportService.importSchema(connectionId, ADMIN_ID).success()).isTrue();

        // PermissionAssignment/AuxPermissionAssignmentは再取り込み前後で一切変更していないが、
        // 主キー構成の変化がSchemaReimportedEvent経由でキャッシュを無効化し、直後のcanCreateが
        // 必ず新しいスキーマ構造を反映する。
        assertThat(resolver.canCreate(userId, connectionId, SCHEMA, TABLE)).isEqualTo(startsWithPrimaryKey);
    }

    // カラムの物理削除によるstaleフラグ設定が、PermissionAssignmentを一切変更せずに
    // resolveEffectiveColumnPermissionsの結果セットから当該カラムを直後に除外することを検証する
    // （EffectivePermissionResolver#resolveEffectiveColumnPermissionsはfindByTableIdAndStaleFalse
    // でstale列を除外するのみで、行自体は削除されない＝SchemaImportService側のP8と整合）。
    @Property(tries = 10)
    void columnRemovalExcludesStaleColumnFromEffectiveColumnPermissionsImmediately(
            @ForAll boolean dropFirstColumn
    ) throws Exception {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        String dbName = "P11COL" + seq;
        try (Connection setup = openConnection(dbName)) {
            createSchema(setup, SCHEMA);
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE " + SCHEMA + "." + TABLE + " (C1 INT, C2 INT)");
            }
        }

        Long connectionId = registerConnection(dbName, seq);
        Long userId = createUser("p11-col-user-" + seq + "@example.com");
        PrincipalRef principal = new PrincipalRef(PrincipalType.USER, userId);

        assertThat(schemaImportService.importSchema(connectionId, ADMIN_ID).success()).isTrue();
        permissionAssignmentService.setPermission(
                ADMIN_ID, principal, connectionId, SCHEMA, Optional.of(TABLE), Optional.empty(), Permission.READ);

        assertThat(resolver.resolveEffectiveColumnPermissions(userId, connectionId, SCHEMA, TABLE))
                .containsOnlyKeys("C1", "C2");

        String droppedColumn = dropFirstColumn ? "C1" : "C2";
        String keptColumn = dropFirstColumn ? "C2" : "C1";
        try (Connection change = openConnection(dbName)) {
            try (Statement st = change.createStatement()) {
                st.execute("ALTER TABLE " + SCHEMA + "." + TABLE + " DROP COLUMN " + droppedColumn);
            }
        }
        assertThat(schemaImportService.importSchema(connectionId, ADMIN_ID).success()).isTrue();

        // PermissionAssignmentは再取り込み前後で一切変更していないが、削除カラムのstale化が
        // SchemaReimportedEvent経由でキャッシュを無効化し、直後のresolveEffectiveColumnPermissions
        // が必ず新しいスキーマ構造（削除カラムの除外）を反映する。
        assertThat(resolver.resolveEffectiveColumnPermissions(userId, connectionId, SCHEMA, TABLE))
                .containsOnlyKeys(keptColumn);
    }

    private Long registerConnection(String dbName, long seq) {
        Instant now = Instant.now();
        RdbmsConnection connection = rdbmsConnectionRepository.save(new RdbmsConnection(
                "p11-connection-" + seq, RdbmsType.H2, "localhost", h2Server.getPort(), dbName,
                "sa", "sa", null, now, now));
        return connection.getId();
    }

    private Long createUser(String email) {
        Instant now = Instant.now();
        User user = userRepository.save(
                new User(email, "hash", Role.USER, UserStatus.APPROVED, now, now));
        return user.getId();
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

    private void createSingleColumnTable(Connection conn, String table, boolean withPrimaryKey) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + SCHEMA + "." + table + " (C1 INT"
                    + (withPrimaryKey ? " PRIMARY KEY" : "") + ")");
        }
    }

    private void dropTable(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE " + SCHEMA + "." + table);
        }
    }

}