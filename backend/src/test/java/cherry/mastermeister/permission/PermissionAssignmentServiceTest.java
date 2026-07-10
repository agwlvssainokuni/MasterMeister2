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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.schema.SchemaColumn;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;
import cherry.mastermeister.userregistration.UserRepository;
import cherry.mastermeister.group.GroupRepository;

/**
 * P3（business-logic-model.md）を検証するプロパティテスト。
 */
class PermissionAssignmentServiceTest {

    private static final AtomicLong PERMISSION_ID_SEQ = new AtomicLong();
    private static final AtomicLong AUX_ID_SEQ = new AtomicLong();

    // P3: 同一(principal, connectionId, schema, table, column)に同一permission値で
    // 複数回setPermissionを呼び出しても、PermissionAssignmentは常に1行のみ存在し値も変化しない。
    @Property(tries = 20)
    void setPermissionIsIdempotent(
            @ForAll("principals") PrincipalRef principal,
            @ForAll("connectionIds") Long connectionId,
            @ForAll("schemaNames") String schema,
            @ForAll("targets") Target target,
            @ForAll("permissions") Permission permission
    ) {
        FakeRepositories repos = new FakeRepositories();
        PermissionAssignmentService service = repos.newService();

        service.setPermission(1L, principal, connectionId, schema, target.table(), target.column(), permission);
        service.setPermission(1L, principal, connectionId, schema, target.table(), target.column(), permission);
        service.setPermission(1L, principal, connectionId, schema, target.table(), target.column(), permission);

        List<PermissionAssignment> matches = repos.permissionAssignments.stream()
                .filter(a -> a.getPrincipalType() == principal.principalType()
                        && a.getPrincipalId().equals(principal.principalId())
                        && a.getConnectionId().equals(connectionId)
                        && a.getSchemaName().equals(schema)
                        && Objects.equals(a.getTableName(), target.table().orElse(null))
                        && Objects.equals(a.getColumnName(), target.column().orElse(null)))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getPermission()).isEqualTo(permission);
    }

    // P3: 同一(principal, connectionId, schema, table, auxType)に同一granted値で
    // 複数回setAuxPermissionを呼び出しても、AuxPermissionAssignmentは常に1行のみ存在し
    // 値も変化しない。
    @Property(tries = 20)
    void setAuxPermissionIsIdempotent(
            @ForAll("principals") PrincipalRef principal,
            @ForAll("connectionIds") Long connectionId,
            @ForAll("schemaNames") String schema,
            @ForAll("tableOptions") Optional<String> table,
            @ForAll("auxTypes") AuxPermissionType auxType,
            @ForAll boolean granted
    ) {
        FakeRepositories repos = new FakeRepositories();
        PermissionAssignmentService service = repos.newService();

        service.setAuxPermission(1L, principal, connectionId, schema, table, auxType, granted);
        service.setAuxPermission(1L, principal, connectionId, schema, table, auxType, granted);
        service.setAuxPermission(1L, principal, connectionId, schema, table, auxType, granted);

        List<AuxPermissionAssignment> matches = repos.auxPermissionAssignments.stream()
                .filter(a -> a.getPrincipalType() == principal.principalType()
                        && a.getPrincipalId().equals(principal.principalId())
                        && a.getConnectionId().equals(connectionId)
                        && a.getSchemaName().equals(schema)
                        && Objects.equals(a.getTableName(), table.orElse(null))
                        && a.getAuxType() == auxType)
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).isGranted()).isEqualTo(granted);
    }

    @Provide
    Arbitrary<PrincipalRef> principals() {
        Arbitrary<PrincipalType> type = Arbitraries.of(PrincipalType.class);
        Arbitrary<Long> id = Arbitraries.longs().between(1L, 1_000_000L);
        return Combinators.combine(type, id).as(PrincipalRef::new);
    }

    @Provide
    Arbitrary<Long> connectionIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    @Provide
    Arbitrary<String> schemaNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
    }

    @Provide
    Arbitrary<Permission> permissions() {
        return Arbitraries.of(Permission.class);
    }

    @Provide
    Arbitrary<AuxPermissionType> auxTypes() {
        return Arbitraries.of(AuxPermissionType.class);
    }

    @Provide
    Arbitrary<Optional<String>> tableOptions() {
        return Arbitraries.oneOf(
                Arbitraries.just(Optional.<String>empty()),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(Optional::of));
    }

    // columnはtableが指定されている場合のみ値を持ちうる（setPermissionはcolumn単体指定を拒否するため）。
    @Provide
    Arbitrary<Target> targets() {
        return tableOptions().flatMap(table -> {
            if (table.isEmpty()) {
                return Arbitraries.just(new Target(table, Optional.empty()));
            }
            Arbitrary<Optional<String>> columnArb = Arbitraries.oneOf(
                    Arbitraries.just(Optional.<String>empty()),
                    Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(Optional::of));
            return columnArb.map(column -> new Target(table, column));
        });
    }

    private record Target(Optional<String> table, Optional<String> column) {
    }

    private static void assignId(Object entity, long id) throws NoSuchFieldException, IllegalAccessException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static final class FakeRepositories {
        final PermissionAssignmentRepository permissionAssignmentRepository = mock(PermissionAssignmentRepository.class);
        final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository = mock(AuxPermissionAssignmentRepository.class);
        final SchemaTableRepository schemaTableRepository = mock(SchemaTableRepository.class);
        final SchemaColumnRepository schemaColumnRepository = mock(SchemaColumnRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final GroupRepository groupRepository = mock(GroupRepository.class);
        final List<PermissionAssignment> permissionAssignments = new ArrayList<>();
        final List<AuxPermissionAssignment> auxPermissionAssignments = new ArrayList<>();

        FakeRepositories() {
            when(permissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                            any(), anyLong(), anyLong(), any(), any(), any()))
                    .thenAnswer(inv -> permissionAssignments.stream()
                            .filter(a -> a.getPrincipalType() == inv.getArgument(0)
                                    && a.getPrincipalId().equals(inv.getArgument(1))
                                    && a.getConnectionId().equals(inv.getArgument(2))
                                    && Objects.equals(a.getSchemaName(), inv.getArgument(3))
                                    && Objects.equals(a.getTableName(), inv.getArgument(4))
                                    && Objects.equals(a.getColumnName(), inv.getArgument(5)))
                            .findFirst());
            when(permissionAssignmentRepository.save(any())).thenAnswer(inv -> {
                PermissionAssignment a = inv.getArgument(0);
                assignId(a, PERMISSION_ID_SEQ.incrementAndGet());
                permissionAssignments.add(a);
                return a;
            });

            when(auxPermissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                            any(), anyLong(), anyLong(), any(), any(), any()))
                    .thenAnswer(inv -> auxPermissionAssignments.stream()
                            .filter(a -> a.getPrincipalType() == inv.getArgument(0)
                                    && a.getPrincipalId().equals(inv.getArgument(1))
                                    && a.getConnectionId().equals(inv.getArgument(2))
                                    && Objects.equals(a.getSchemaName(), inv.getArgument(3))
                                    && Objects.equals(a.getTableName(), inv.getArgument(4))
                                    && a.getAuxType() == inv.getArgument(5))
                            .findFirst());
            when(auxPermissionAssignmentRepository.save(any())).thenAnswer(inv -> {
                AuxPermissionAssignment a = inv.getArgument(0);
                assignId(a, AUX_ID_SEQ.incrementAndGet());
                auxPermissionAssignments.add(a);
                return a;
            });

            SchemaTable fakeTable = mock(SchemaTable.class);
            when(fakeTable.getId()).thenReturn(1L);
            when(schemaTableRepository.existsByConnectionIdAndSchemaName(anyLong(), anyString())).thenReturn(true);
            when(schemaTableRepository.findByConnectionIdAndSchemaNameAndTableName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.of(fakeTable));
            when(schemaColumnRepository.findByTableIdAndColumnName(anyLong(), anyString()))
                    .thenReturn(Optional.of(mock(SchemaColumn.class)));
            when(userRepository.existsById(anyLong())).thenReturn(true);
            when(groupRepository.existsById(anyLong())).thenReturn(true);
        }

        PermissionAssignmentService newService() {
            return new PermissionAssignmentService(
                    permissionAssignmentRepository, auxPermissionAssignmentRepository,
                    schemaTableRepository, schemaColumnRepository, userRepository, groupRepository,
                    mock(AuditLogService.class));
        }
    }

}