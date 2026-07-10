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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.group.GroupMember;
import cherry.mastermeister.group.GroupMemberRepository;
import cherry.mastermeister.schema.SchemaColumn;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;
import cherry.mastermeister.schema.TableType;

/**
 * P7・P8・P9（business-logic-model.md）を検証するプロパティテスト。
 */
class EffectivePermissionResolverTest {

    private static final List<Long> GROUP_IDS = List.of(1L, 2L, 3L, 4L);

    // P7（前半）: ユーザが複数グループに所属する場合、主権限のグループ合成（最大値）は
    // グループの評価順序に依存しない（business-rules.md 2.5手順3）。
    @Property(tries = 20)
    void groupCompositionMainPermissionIsCommutative(
            @ForAll("permissionsForGroups") List<Permission> permissions,
            @ForAll("groupOrder") List<Long> order1,
            @ForAll("groupOrder") List<Long> order2
    ) {
        Long userId = 1L;
        Long connectionId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        Map<Long, Permission> permissionByGroup = zipGroupPermissions(permissions);

        Permission result1 = resolveMainPermissionWithGroupOrder(
                order1, permissionByGroup, userId, connectionId, schema, table);
        Permission result2 = resolveMainPermissionWithGroupOrder(
                order2, permissionByGroup, userId, connectionId, schema, table);

        Permission expected = permissions.stream().reduce(Permission.NONE, Permission::max);
        assertThat(result1).isEqualTo(expected);
        assertThat(result2).isEqualTo(expected);
    }

    // P7（後半）: 補助権限のグループ合成（OR）はグループの評価順序に依存しない
    // （business-rules.md 2.5手順2・3）。canCreateは主キーなしテーブルでは補助権限Cのみで
    // 判定される（P9、2.5手順5の例外規定）ため、主権限の影響を排除して補助権限の合成のみを
    // 検証する手段として利用する。
    @Property(tries = 20)
    void groupCompositionAuxPermissionIsCommutative(
            @ForAll("grantedForGroups") List<Boolean> granted,
            @ForAll("groupOrder") List<Long> order1,
            @ForAll("groupOrder") List<Long> order2
    ) throws Exception {
        Long userId = 1L;
        Long connectionId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        Map<Long, Boolean> grantedByGroup = zipGroupGranted(granted);

        boolean result1 = resolveAuxCreateWithGroupOrder(order1, grantedByGroup, userId, connectionId, schema, table);
        boolean result2 = resolveAuxCreateWithGroupOrder(order2, grantedByGroup, userId, connectionId, schema, table);

        boolean expected = granted.stream().anyMatch(Boolean::booleanValue);
        assertThat(result1).isEqualTo(expected);
        assertThat(result2).isEqualTo(expected);
    }

    // P8（前半）: テーブル階層にユーザの明示的個別設定が存在する場合、
    // resolveEffectiveTablePermissionの結果は常にその値と一致し、グループ合成結果
    // （どれほど緩い値であっても）によって上書きされない（business-rules.md 2.5手順4）。
    @Property(tries = 20)
    void userExplicitTableLevelOverridesGroupComposition(
            @ForAll("permissions") Permission userTablePermission,
            @ForAll("permissionsForGroups") List<Permission> groupPermissions
    ) {
        FakeRepositories repos = new FakeRepositories();
        Long userId = 1L;
        Long connectionId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        Instant now = Instant.now();

        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, null, userTablePermission, now));
        for (int i = 0; i < GROUP_IDS.size(); i++) {
            Long groupId = GROUP_IDS.get(i);
            repos.groupMembers.add(new GroupMember(groupId, userId, now));
            repos.permissionAssignments.add(new PermissionAssignment(
                    PrincipalType.GROUP, groupId, connectionId, schema, table, null, groupPermissions.get(i), now));
        }

        Permission result = repos.newResolver().resolveEffectiveTablePermission(userId, connectionId, schema, table);

        assertThat(result).isEqualTo(userTablePermission);
    }

    // P8（後半）: カラム階層にユーザの明示的個別設定が存在する場合、
    // resolveEffectiveColumnPermissionsの結果は常にその値と一致し、同ユーザのテーブル階層
    // 設定やグループのカラム階層設定によって上書きされない（継承はカラムが最下位、
    // business-rules.md 2.5手順1・4）。
    @Property(tries = 20)
    void userExplicitColumnLevelOverridesTableLevelAndGroups(
            @ForAll("permissions") Permission userColumnPermission,
            @ForAll("permissions") Permission userTablePermission,
            @ForAll("permissions") Permission groupColumnPermission
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long userId = 1L;
        Long connectionId = 1L;
        Long groupId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        String column = "C1";
        Instant now = Instant.now();

        repos.schemaTables.add(newSchemaTable(100L, connectionId, schema, table));
        repos.schemaColumns.add(newSchemaColumn(100L, column, 1, null));

        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, column, userColumnPermission, now));
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, null, userTablePermission, now));
        repos.groupMembers.add(new GroupMember(groupId, userId, now));
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.GROUP, groupId, connectionId, schema, table, column, groupColumnPermission, now));

        Map<String, Permission> result = repos.newResolver()
                .resolveEffectiveColumnPermissions(userId, connectionId, schema, table);

        assertThat(result).containsEntry(column, userColumnPermission);
    }

    // P9（前半）: 主キーを持たないテーブルに対しcanDeleteは常にfalseを返す
    // （補助権限D・主権限の値によらない。business-rules.md 2.5手順6の例外規定）。
    @Property(tries = 20)
    void canDeleteIsAlwaysFalseForTableWithoutPrimaryKey(
            @ForAll boolean auxDeleteGranted,
            @ForAll("permissions") Permission mainPermission
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long userId = 1L;
        Long connectionId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        Instant now = Instant.now();

        repos.schemaTables.add(newSchemaTable(200L, connectionId, schema, table));
        repos.schemaColumns.add(newSchemaColumn(200L, "C1", 1, null));

        repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, AuxPermissionType.DELETE, auxDeleteGranted, now));
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, "C1", mainPermission, now));

        boolean result = repos.newResolver().canDelete(userId, connectionId, schema, table);

        assertThat(result).isFalse();
    }

    // P9（後半）: 主キーを持たないテーブルに対しcanCreateは補助権限Cの値のみで決まり、
    // 主権限の値によらない（主キー構成カラムが存在しないため全カラムAND判定が
    // 適用されない例外規定、business-rules.md 2.5手順5）。
    @Property(tries = 20)
    void canCreateForTableWithoutPrimaryKeyEqualsAuxPermissionRegardlessOfMainPermission(
            @ForAll boolean auxCreateGranted,
            @ForAll("permissions") Permission mainPermission
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long userId = 1L;
        Long connectionId = 1L;
        String schema = "SCHEMA1";
        String table = "T1";
        Instant now = Instant.now();

        repos.schemaTables.add(newSchemaTable(300L, connectionId, schema, table));
        repos.schemaColumns.add(newSchemaColumn(300L, "C1", 1, null));

        repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, AuxPermissionType.CREATE, auxCreateGranted, now));
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, userId, connectionId, schema, table, "C1", mainPermission, now));

        boolean result = repos.newResolver().canCreate(userId, connectionId, schema, table);

        assertThat(result).isEqualTo(auxCreateGranted);
    }

    @Provide
    Arbitrary<Permission> permissions() {
        return Arbitraries.of(Permission.class);
    }

    @Provide
    Arbitrary<List<Permission>> permissionsForGroups() {
        return Arbitraries.of(Permission.class).list().ofSize(GROUP_IDS.size());
    }

    @Provide
    Arbitrary<List<Boolean>> grantedForGroups() {
        return Arbitraries.of(true, false).list().ofSize(GROUP_IDS.size());
    }

    @Provide
    Arbitrary<List<Long>> groupOrder() {
        return Arbitraries.shuffle(GROUP_IDS);
    }

    private static Map<Long, Permission> zipGroupPermissions(List<Permission> permissions) {
        Map<Long, Permission> map = new LinkedHashMap<>();
        for (int i = 0; i < GROUP_IDS.size(); i++) {
            map.put(GROUP_IDS.get(i), permissions.get(i));
        }
        return map;
    }

    private static Map<Long, Boolean> zipGroupGranted(List<Boolean> granted) {
        Map<Long, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < GROUP_IDS.size(); i++) {
            map.put(GROUP_IDS.get(i), granted.get(i));
        }
        return map;
    }

    private static Permission resolveMainPermissionWithGroupOrder(
            List<Long> order, Map<Long, Permission> permissionByGroup,
            Long userId, Long connectionId, String schema, String table
    ) {
        FakeRepositories repos = new FakeRepositories();
        Instant now = Instant.now();
        for (Long groupId : order) {
            repos.groupMembers.add(new GroupMember(groupId, userId, now));
            repos.permissionAssignments.add(new PermissionAssignment(
                    PrincipalType.GROUP, groupId, connectionId, schema, table, null, permissionByGroup.get(groupId), now));
        }
        return repos.newResolver().resolveEffectiveTablePermission(userId, connectionId, schema, table);
    }

    private static boolean resolveAuxCreateWithGroupOrder(
            List<Long> order, Map<Long, Boolean> grantedByGroup,
            Long userId, Long connectionId, String schema, String table
    ) throws ReflectiveOperationException {
        FakeRepositories repos = new FakeRepositories();
        Instant now = Instant.now();
        repos.schemaTables.add(newSchemaTable(400L, connectionId, schema, table));
        // 主キーなし（PKカラム未登録）。canCreateが補助権限Cのみで判定される状態にし、
        // 主権限を一切介在させずに補助権限のグループ合成のみを検証する。
        for (Long groupId : order) {
            repos.groupMembers.add(new GroupMember(groupId, userId, now));
            repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                    PrincipalType.GROUP, groupId, connectionId, schema, table,
                    AuxPermissionType.CREATE, grantedByGroup.get(groupId), now));
        }
        return repos.newResolver().canCreate(userId, connectionId, schema, table);
    }

    private static SchemaTable newSchemaTable(
            Long id, Long connectionId, String schema, String table
    ) throws ReflectiveOperationException {
        SchemaTable schemaTable = new SchemaTable(
                connectionId, schema, table, TableType.TABLE, null, false, Instant.now(), Instant.now());
        assignId(schemaTable, id);
        return schemaTable;
    }

    private static SchemaColumn newSchemaColumn(
            Long tableId, String columnName, int ordinalPosition, Integer primaryKeySequence
    ) {
        return new SchemaColumn(
                tableId, columnName, "VARCHAR", true, null, ordinalPosition, primaryKeySequence, false,
                Instant.now(), Instant.now());
    }

    private static void assignId(Object entity, long id) throws NoSuchFieldException, IllegalAccessException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static final class FakeRepositories {
        final PermissionAssignmentRepository permissionAssignmentRepository = mock(PermissionAssignmentRepository.class);
        final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository =
                mock(AuxPermissionAssignmentRepository.class);
        final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        final SchemaTableRepository schemaTableRepository = mock(SchemaTableRepository.class);
        final SchemaColumnRepository schemaColumnRepository = mock(SchemaColumnRepository.class);
        final List<PermissionAssignment> permissionAssignments = new ArrayList<>();
        final List<AuxPermissionAssignment> auxPermissionAssignments = new ArrayList<>();
        final List<GroupMember> groupMembers = new ArrayList<>();
        final List<SchemaTable> schemaTables = new ArrayList<>();
        final List<SchemaColumn> schemaColumns = new ArrayList<>();

        FakeRepositories() {
            when(permissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                            any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> permissionAssignments.stream()
                            .filter(a -> a.getPrincipalType() == inv.getArgument(0)
                                    && a.getPrincipalId().equals(inv.getArgument(1))
                                    && a.getConnectionId().equals(inv.getArgument(2))
                                    && a.getSchemaName().equals(inv.getArgument(3))
                                    && Objects.equals(a.getTableName(), inv.getArgument(4))
                                    && Objects.equals(a.getColumnName(), inv.getArgument(5)))
                            .findFirst());
            when(auxPermissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                            any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> auxPermissionAssignments.stream()
                            .filter(a -> a.getPrincipalType() == inv.getArgument(0)
                                    && a.getPrincipalId().equals(inv.getArgument(1))
                                    && a.getConnectionId().equals(inv.getArgument(2))
                                    && a.getSchemaName().equals(inv.getArgument(3))
                                    && Objects.equals(a.getTableName(), inv.getArgument(4))
                                    && a.getAuxType() == inv.getArgument(5))
                            .findFirst());
            when(groupMemberRepository.findByUserId(any())).thenAnswer(inv -> groupMembers.stream()
                    .filter(m -> m.getUserId().equals(inv.getArgument(0)))
                    .toList());
            when(schemaTableRepository.findByConnectionIdAndSchemaNameAndTableName(any(), any(), any()))
                    .thenAnswer(inv -> schemaTables.stream()
                            .filter(t -> t.getConnectionId().equals(inv.getArgument(0))
                                    && t.getSchemaName().equals(inv.getArgument(1))
                                    && t.getTableName().equals(inv.getArgument(2)))
                            .findFirst());
            when(schemaColumnRepository.findByTableIdAndStaleFalse(any())).thenAnswer(inv -> schemaColumns.stream()
                    .filter(c -> c.getTableId().equals(inv.getArgument(0)))
                    .toList());
        }

        EffectivePermissionResolver newResolver() {
            return new EffectivePermissionResolver(
                    permissionAssignmentRepository, auxPermissionAssignmentRepository, groupMemberRepository,
                    schemaTableRepository, schemaColumnRepository);
        }
    }

}
