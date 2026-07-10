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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cherry.mastermeister.group.GroupRepository;
import cherry.mastermeister.group.Group;
import cherry.mastermeister.group.GroupService;
import cherry.mastermeister.schema.SchemaColumn;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;
import cherry.mastermeister.schema.TableType;
import cherry.mastermeister.userregistration.Role;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;
import cherry.mastermeister.userregistration.UserStatus;

/**
 * P10（business-logic-model.md）を検証するプロパティテスト。
 * フロー1（グループ所属変更）・フロー2（{@code setPermission}/{@code setAuxPermission}）・
 * フロー4（{@code importPermissionsFromYaml}）いずれの書き込み操作の直後も、
 * {@code EffectivePermissionResolver}（実キャッシュBean、{@link PermissionCacheInvalidationListener}
 * 経由の無効化を含む）が必ず最新値を返すことを検証する。
 */
@SpringBootTest
@JqwikSpringSupport
class PermissionCacheConsistencyTest {

    private static final Long ADMIN_ID = 1L;
    // 実DB（ファイルベースH2、テスト実行間で永続化される）を使うため、SEQ単独では
    // 過去の実行で残った行のunique制約（email/name/schemaName等）と衝突しうる。
    // 起動ごとに変わるSystem.nanoTime()を混ぜて実行間でも重複しないキーを生成する。
    private static final long RUN_SEED = System.nanoTime();
    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private EffectivePermissionResolver resolver;

    @Autowired
    private PermissionAssignmentService permissionAssignmentService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private SchemaTableRepository schemaTableRepository;

    @Autowired
    private SchemaColumnRepository schemaColumnRepository;

    // フロー2（setPermission）: キャッシュに旧値が乗った状態で書き込みを行っても、
    // 直後のresolveEffectiveTablePermissionは必ず新値を返す。
    @Property(tries = 10)
    void setPermissionWriteIsImmediatelyVisibleAfterCommit(
            @ForAll("permissions") Permission initial,
            @ForAll("permissions") Permission updated
    ) {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        Long userId = createUser("cache-test-user-" + seq + "@example.com");
        Long connectionId = 100_000L + seq;
        String schema = "SCHEMA_" + seq;
        String table = "T1";
        createTable(connectionId, schema, table);
        PrincipalRef principal = new PrincipalRef(PrincipalType.USER, userId);

        permissionAssignmentService.setPermission(
                ADMIN_ID, principal, connectionId, schema, Optional.of(table), Optional.empty(), initial);
        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(initial);

        permissionAssignmentService.setPermission(
                ADMIN_ID, principal, connectionId, schema, Optional.of(table), Optional.empty(), updated);
        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(updated);
    }

    // フロー2（setAuxPermission）: 主キーなしテーブル（canCreateが補助権限Cのみで決まる、
    // P9と同じ性質）を使い、キャッシュに旧値が乗った状態で書き込みを行っても、
    // 直後のcanCreateは必ず新値を反映する。
    @Property(tries = 10)
    void setAuxPermissionWriteIsImmediatelyVisibleAfterCommit(
            @ForAll boolean initialGranted,
            @ForAll boolean updatedGranted
    ) {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        Long userId = createUser("cache-test-user-" + seq + "@example.com");
        Long connectionId = 200_000L + seq;
        String schema = "SCHEMA_" + seq;
        String table = "T1";
        createTableWithoutPrimaryKey(connectionId, schema, table);
        PrincipalRef principal = new PrincipalRef(PrincipalType.USER, userId);

        permissionAssignmentService.setAuxPermission(
                ADMIN_ID, principal, connectionId, schema, Optional.of(table),
                AuxPermissionType.CREATE, initialGranted);
        assertThat(resolver.canCreate(userId, connectionId, schema, table)).isEqualTo(initialGranted);

        permissionAssignmentService.setAuxPermission(
                ADMIN_ID, principal, connectionId, schema, Optional.of(table),
                AuxPermissionType.CREATE, updatedGranted);
        assertThat(resolver.canCreate(userId, connectionId, schema, table)).isEqualTo(updatedGranted);
    }

    // フロー1（グループ所属変更）: GroupService.addUserToGroup/removeUserFromGroupが発行する
    // GroupChangedEventをPermissionCacheInvalidationListener（@TransactionalEventListener
    // (phase = AFTER_COMMIT)）が捕捉してキャッシュを無効化することを検証する。キャッシュに
    // 所属変更前の実効権限（NONE）が乗った状態でグループに追加しても、直後の
    // resolveEffectiveTablePermissionは必ずグループの権限設定を反映する。
    @Property(tries = 10)
    void groupMembershipChangeIsImmediatelyVisibleAfterCommit(
            @ForAll("permissions") Permission groupPermission
    ) {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        Long userId = createUser("cache-test-user-" + seq + "@example.com");
        Long groupId = createGroup("cache-test-group-" + seq);
        Long connectionId = 300_000L + seq;
        String schema = "SCHEMA_" + seq;
        String table = "T1";
        createTable(connectionId, schema, table);

        permissionAssignmentService.setPermission(
                ADMIN_ID, new PrincipalRef(PrincipalType.GROUP, groupId), connectionId, schema,
                Optional.of(table), Optional.empty(), groupPermission);

        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(Permission.NONE);

        groupService.addUserToGroup(ADMIN_ID, groupId, userId);
        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(groupPermission);

        groupService.removeUserFromGroup(ADMIN_ID, groupId, userId);
        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(Permission.NONE);
    }

    // フロー4（importPermissionsFromYaml）: 既存のPermissionAssignment行をYAMLインポートで
    // 総入れ替えした直後も、resolveEffectiveTablePermissionは必ず新値を反映する。
    @Property(tries = 10)
    void importPermissionsFromYamlWriteIsImmediatelyVisibleAfterCommit(
            @ForAll("permissions") Permission initial,
            @ForAll("permissions") Permission updated
    ) throws Exception {
        long seq = RUN_SEED + SEQ.incrementAndGet();
        String email = "cache-test-user-" + seq + "@example.com";
        Long userId = createUser(email);
        Long connectionId = 400_000L + seq;
        String schema = "SCHEMA_" + seq;
        String table = "T1";
        createTable(connectionId, schema, table);
        PrincipalRef principal = new PrincipalRef(PrincipalType.USER, userId);

        permissionAssignmentService.setPermission(
                ADMIN_ID, principal, connectionId, schema, Optional.of(table), Optional.empty(), initial);
        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(initial);

        byte[] yaml = buildYaml(email, schema, table, updated);
        ImportResult result = permissionAssignmentService.importPermissionsFromYaml(ADMIN_ID, connectionId, yaml);
        assertThat(result.success()).isTrue();

        assertThat(resolver.resolveEffectiveTablePermission(userId, connectionId, schema, table))
                .isEqualTo(updated);
    }

    @Provide
    Arbitrary<Permission> permissions() {
        return Arbitraries.of(Permission.class);
    }

    private Long createUser(String email) {
        Instant now = Instant.now();
        User user = userRepository.save(
                new User(email, "hash", Role.USER, UserStatus.APPROVED, now, now));
        return user.getId();
    }

    private Long createGroup(String name) {
        Group group = groupRepository.save(new Group(name, Instant.now()));
        return group.getId();
    }

    private void createTable(Long connectionId, String schema, String table) {
        schemaTableRepository.save(new SchemaTable(
                connectionId, schema, table, TableType.TABLE, null, false, Instant.now(), Instant.now()));
    }

    private void createTableWithoutPrimaryKey(Long connectionId, String schema, String table) {
        SchemaTable schemaTable = schemaTableRepository.save(new SchemaTable(
                connectionId, schema, table, TableType.TABLE, null, false, Instant.now(), Instant.now()));
        schemaColumnRepository.save(new SchemaColumn(
                schemaTable.getId(), "C1", "VARCHAR", true, null, 1, null, false,
                Instant.now(), Instant.now()));
    }

    private static byte[] buildYaml(String email, String schema, String table, Permission permission)
            throws Exception {
        PermissionEntryYaml entry = new PermissionEntryYaml();
        entry.setSchema(schema);
        entry.setTable(table);
        entry.setPermission(permission.name());

        PrincipalYaml principalYaml = new PrincipalYaml();
        principalYaml.setType(PrincipalType.USER.name());
        principalYaml.setEmail(email);
        principalYaml.setPermissions(List.of(entry));
        principalYaml.setAuxPermissions(List.of());

        PermissionYamlDocument document = new PermissionYamlDocument();
        document.setPrincipals(List.of(principalYaml));

        return new YAMLMapper().writeValueAsBytes(document);
    }

}