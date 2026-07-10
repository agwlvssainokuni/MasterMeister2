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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.group.Group;
import cherry.mastermeister.group.GroupRepository;
import cherry.mastermeister.schema.SchemaColumn;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;

/**
 * P3・P4・P5・P6（business-logic-model.md）を検証するプロパティテスト。
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

    // P4: exportPermissionsAsYamlで得たYAMLをそのまま同じ接続へimportPermissionsFromYaml
    // した結果、(principalType, principal識別子, schema, table, column, permission)／
    // (..., auxType, granted)のタプル集合はエクスポート前と完全一致する。
    @Property(tries = 20)
    void exportImportRoundTrip(
            @ForAll("permCandidateSubsets") List<PermCandidate> userPerms,
            @ForAll("auxCandidateSubsets") List<AuxCandidate> userAux,
            @ForAll("permCandidateSubsets") List<PermCandidate> groupPerms,
            @ForAll("auxCandidateSubsets") List<AuxCandidate> groupAux
    ) {
        FakeRepositories repos = new FakeRepositories();
        Long connectionId = 1L;
        Long otherConnectionId = 999L;
        String schema = "SCHEMA1";
        User user = repos.registerUser(1001L, "roundtrip-user@example.com");
        Group group = repos.registerGroup(2002L, "roundtrip-group");

        Instant now = Instant.now();
        seedPermissions(repos, PrincipalType.USER, user.getId(), connectionId, schema, userPerms, now);
        seedAuxPermissions(repos, PrincipalType.USER, user.getId(), connectionId, schema, userAux, now);
        seedPermissions(repos, PrincipalType.GROUP, group.getId(), connectionId, schema, groupPerms, now);
        seedAuxPermissions(repos, PrincipalType.GROUP, group.getId(), connectionId, schema, groupAux, now);
        // 他接続のデータ（対象外であることの確認用）
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, user.getId(), otherConnectionId, schema, "T1", null, Permission.READ, now));

        Set<PermissionTuple> before = snapshotPermissions(repos, connectionId);
        Set<AuxPermissionTuple> beforeAux = snapshotAuxPermissions(repos, connectionId);

        PermissionAssignmentService service = repos.newService();
        byte[] yaml = service.exportPermissionsAsYaml(1L, connectionId);
        ImportResult result = service.importPermissionsFromYaml(1L, connectionId, yaml);

        assertThat(result.success()).isTrue();
        assertThat(snapshotPermissions(repos, connectionId)).isEqualTo(before);
        assertThat(snapshotAuxPermissions(repos, connectionId)).isEqualTo(beforeAux);
        assertThat(repos.permissionAssignments).anyMatch(p -> p.getConnectionId().equals(otherConnectionId));
    }

    // P5: permissions配下に同一(principal, schema, table, column)の組み合わせが2回以上
    // 出現するYAMLは、内容（permission値）によらず常にインポート失敗となる。
    @Property(tries = 20)
    void importRejectsDuplicatePermissionEntries(
            @ForAll("permissions") Permission first,
            @ForAll("permissions") Permission second
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long connectionId = 1L;
        User user = repos.registerUser(1001L, "dup-perm-user@example.com");

        PermissionEntryYaml entry1 = new PermissionEntryYaml();
        entry1.setSchema("SCHEMA1");
        entry1.setTable("T1");
        entry1.setPermission(first.name());
        PermissionEntryYaml entry2 = new PermissionEntryYaml();
        entry2.setSchema("SCHEMA1");
        entry2.setTable("T1");
        entry2.setPermission(second.name());

        PrincipalYaml principal = new PrincipalYaml();
        principal.setType(PrincipalType.USER.name());
        principal.setEmail(user.getEmail());
        principal.setPermissions(List.of(entry1, entry2));
        PermissionYamlDocument document = new PermissionYamlDocument();
        document.setPrincipals(List.of(principal));
        byte[] yaml = new YAMLMapper().writeValueAsBytes(document);

        PermissionAssignmentService service = transactionalProxy(repos.newService());
        ImportResult result = service.importPermissionsFromYaml(1L, connectionId, yaml);

        assertThat(result.success()).isFalse();
        assertThat(repos.permissionAssignments).isEmpty();
    }

    // P5: auxPermissions配下に同一(principal, schema, table, auxType)の組み合わせが2回以上
    // 出現するYAMLは、granted値によらず常にインポート失敗となる。
    @Property(tries = 20)
    void importRejectsDuplicateAuxPermissionEntries(
            @ForAll boolean first,
            @ForAll boolean second
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long connectionId = 1L;
        User user = repos.registerUser(1001L, "dup-aux-user@example.com");

        AuxPermissionEntryYaml entry1 = new AuxPermissionEntryYaml();
        entry1.setSchema("SCHEMA1");
        entry1.setTable("T1");
        entry1.setType(AuxPermissionType.CREATE.name());
        entry1.setGranted(first);
        AuxPermissionEntryYaml entry2 = new AuxPermissionEntryYaml();
        entry2.setSchema("SCHEMA1");
        entry2.setTable("T1");
        entry2.setType(AuxPermissionType.CREATE.name());
        entry2.setGranted(second);

        PrincipalYaml principal = new PrincipalYaml();
        principal.setType(PrincipalType.USER.name());
        principal.setEmail(user.getEmail());
        principal.setAuxPermissions(List.of(entry1, entry2));
        PermissionYamlDocument document = new PermissionYamlDocument();
        document.setPrincipals(List.of(principal));
        byte[] yaml = new YAMLMapper().writeValueAsBytes(document);

        PermissionAssignmentService service = transactionalProxy(repos.newService());
        ImportResult result = service.importPermissionsFromYaml(1L, connectionId, yaml);

        assertThat(result.success()).isFalse();
        assertThat(repos.auxPermissionAssignments).isEmpty();
    }

    // P6: 検証を通過したインポート成功後、対象connectionIdのPermissionAssignment/
    // AuxPermissionAssignment集合はYAMLの内容と完全に一致し、インポート前に存在した
    // （YAMLに含まれない）行は1件も残らない。
    @Property(tries = 20)
    void importReplacesAllExistingRowsForConnection(
            @ForAll("permCandidateSubsets") List<PermCandidate> newPerms,
            @ForAll("auxCandidateSubsets") List<AuxCandidate> newAux
    ) {
        FakeRepositories repos = new FakeRepositories();
        Long connectionId = 1L;
        Long otherConnectionId = 999L;
        String schema = "SCHEMA1";
        User user = repos.registerUser(1001L, "replace-user@example.com");

        Instant now = Instant.now();
        // インポート前に存在する行（新YAMLには含まれない組み合わせ＝置換後は残らないはず）
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, user.getId(), connectionId, "OLD_SCHEMA", "OLD_TABLE", null, Permission.UPDATE, now));
        repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                PrincipalType.USER, user.getId(), connectionId, "OLD_SCHEMA", "OLD_TABLE", AuxPermissionType.DELETE, true, now));
        // 他接続の行（対象外なので変化しないはず）
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.USER, user.getId(), otherConnectionId, schema, "T1", null, Permission.READ, now));

        PrincipalYaml principal = new PrincipalYaml();
        principal.setType(PrincipalType.USER.name());
        principal.setEmail(user.getEmail());
        principal.setPermissions(newPerms.stream().map(c -> toEntryYaml(schema, c)).toList());
        principal.setAuxPermissions(newAux.stream().map(c -> toEntryYaml(schema, c)).toList());
        PermissionYamlDocument document = new PermissionYamlDocument();
        document.setPrincipals(List.of(principal));
        byte[] yaml;
        try {
            yaml = new YAMLMapper().writeValueAsBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        PermissionAssignmentService service = repos.newService();
        ImportResult result = service.importPermissionsFromYaml(1L, connectionId, yaml);

        assertThat(result.success()).isTrue();
        Set<PermissionTuple> expectedPerms = newPerms.stream()
                .map(c -> new PermissionTuple(
                        PrincipalType.USER, user.getId(), schema, c.table().orElse(null), c.column().orElse(null), c.permission()))
                .collect(Collectors.toSet());
        Set<AuxPermissionTuple> expectedAux = newAux.stream()
                .map(c -> new AuxPermissionTuple(
                        PrincipalType.USER, user.getId(), schema, c.table().orElse(null), c.auxType(), c.granted()))
                .collect(Collectors.toSet());
        assertThat(snapshotPermissions(repos, connectionId)).isEqualTo(expectedPerms);
        assertThat(snapshotAuxPermissions(repos, connectionId)).isEqualTo(expectedAux);
        assertThat(repos.permissionAssignments).noneMatch(
                p -> p.getConnectionId().equals(connectionId) && "OLD_TABLE".equals(p.getTableName()));
        assertThat(repos.auxPermissionAssignments).noneMatch(
                p -> p.getConnectionId().equals(connectionId) && "OLD_TABLE".equals(p.getTableName()));
        assertThat(repos.permissionAssignments).anyMatch(p -> p.getConnectionId().equals(otherConnectionId));
    }

    private static final List<PermCandidate> PERM_CANDIDATES = List.of(
            new PermCandidate(Optional.empty(), Optional.empty(), Permission.READ),
            new PermCandidate(Optional.of("T1"), Optional.empty(), Permission.UPDATE),
            new PermCandidate(Optional.of("T1"), Optional.of("C1"), Permission.NONE),
            new PermCandidate(Optional.of("T2"), Optional.empty(), Permission.READ));

    private static final List<AuxCandidate> AUX_CANDIDATES = List.of(
            new AuxCandidate(Optional.empty(), AuxPermissionType.CREATE, true),
            new AuxCandidate(Optional.of("T1"), AuxPermissionType.CREATE, false),
            new AuxCandidate(Optional.of("T1"), AuxPermissionType.DELETE, true),
            new AuxCandidate(Optional.of("T2"), AuxPermissionType.DELETE, false));

    @Provide
    Arbitrary<List<PermCandidate>> permCandidateSubsets() {
        return Arbitraries.of(PERM_CANDIDATES).list().ofMinSize(0).ofMaxSize(PERM_CANDIDATES.size()).uniqueElements();
    }

    @Provide
    Arbitrary<List<AuxCandidate>> auxCandidateSubsets() {
        return Arbitraries.of(AUX_CANDIDATES).list().ofMinSize(0).ofMaxSize(AUX_CANDIDATES.size()).uniqueElements();
    }

    private record PermCandidate(Optional<String> table, Optional<String> column, Permission permission) {
    }

    private record AuxCandidate(Optional<String> table, AuxPermissionType auxType, boolean granted) {
    }

    private record PermissionTuple(
            PrincipalType principalType, Long principalId, String schema, String table, String column, Permission permission) {
    }

    private record AuxPermissionTuple(
            PrincipalType principalType, Long principalId, String schema, String table, AuxPermissionType auxType, boolean granted) {
    }

    private static void seedPermissions(
            FakeRepositories repos, PrincipalType principalType, Long principalId, Long connectionId,
            String schema, List<PermCandidate> candidates, Instant now
    ) {
        for (PermCandidate c : candidates) {
            repos.permissionAssignments.add(new PermissionAssignment(
                    principalType, principalId, connectionId, schema,
                    c.table().orElse(null), c.column().orElse(null), c.permission(), now));
        }
    }

    private static void seedAuxPermissions(
            FakeRepositories repos, PrincipalType principalType, Long principalId, Long connectionId,
            String schema, List<AuxCandidate> candidates, Instant now
    ) {
        for (AuxCandidate c : candidates) {
            repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                    principalType, principalId, connectionId, schema,
                    c.table().orElse(null), c.auxType(), c.granted(), now));
        }
    }

    private static PermissionEntryYaml toEntryYaml(String schema, PermCandidate c) {
        PermissionEntryYaml entry = new PermissionEntryYaml();
        entry.setSchema(schema);
        entry.setTable(c.table().orElse(null));
        entry.setColumn(c.column().orElse(null));
        entry.setPermission(c.permission().name());
        return entry;
    }

    private static AuxPermissionEntryYaml toEntryYaml(String schema, AuxCandidate c) {
        AuxPermissionEntryYaml entry = new AuxPermissionEntryYaml();
        entry.setSchema(schema);
        entry.setTable(c.table().orElse(null));
        entry.setType(c.auxType().name());
        entry.setGranted(c.granted());
        return entry;
    }

    private static Set<PermissionTuple> snapshotPermissions(FakeRepositories repos, Long connectionId) {
        return repos.permissionAssignments.stream()
                .filter(a -> a.getConnectionId().equals(connectionId))
                .map(a -> new PermissionTuple(
                        a.getPrincipalType(), a.getPrincipalId(), a.getSchemaName(), a.getTableName(), a.getColumnName(), a.getPermission()))
                .collect(Collectors.toSet());
    }

    private static Set<AuxPermissionTuple> snapshotAuxPermissions(FakeRepositories repos, Long connectionId) {
        return repos.auxPermissionAssignments.stream()
                .filter(a -> a.getConnectionId().equals(connectionId))
                .map(a -> new AuxPermissionTuple(
                        a.getPrincipalType(), a.getPrincipalId(), a.getSchemaName(), a.getTableName(), a.getAuxType(), a.isGranted()))
                .collect(Collectors.toSet());
    }

    // importPermissionsFromYamlは検証失敗時にTransactionAspectSupport.currentTransactionStatus()
    // でロールバックマークを行うため、実際の@Transactional AOPプロキシ経由で呼び出す必要がある
    // （素のMockito呼び出しではNoTransactionExceptionとなる）。
    private static PermissionAssignmentService transactionalProxy(PermissionAssignmentService target) {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        TransactionManager transactionManager = new NoopTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor(transactionManager, source);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (PermissionAssignmentService) factory.getProxy();
    }

    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
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
        final Map<Long, User> usersById = new HashMap<>();
        final Map<String, User> usersByEmail = new HashMap<>();
        final Map<Long, Group> groupsById = new HashMap<>();
        final Map<String, Group> groupsByName = new HashMap<>();

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

            when(permissionAssignmentRepository.findByConnectionId(anyLong())).thenAnswer(inv -> permissionAssignments.stream()
                    .filter(a -> a.getConnectionId().equals(inv.getArgument(0)))
                    .toList());
            doAnswer(inv -> {
                Long connectionId = inv.getArgument(0);
                permissionAssignments.removeIf(a -> a.getConnectionId().equals(connectionId));
                return null;
            }).when(permissionAssignmentRepository).deleteByConnectionId(anyLong());
            when(permissionAssignmentRepository.saveAll(any())).thenAnswer(inv -> {
                List<PermissionAssignment> list = new ArrayList<>();
                ((Iterable<PermissionAssignment>) inv.getArgument(0)).forEach(list::add);
                for (PermissionAssignment a : list) {
                    assignId(a, PERMISSION_ID_SEQ.incrementAndGet());
                }
                permissionAssignments.addAll(list);
                return list;
            });

            when(auxPermissionAssignmentRepository.findByConnectionId(anyLong())).thenAnswer(inv -> auxPermissionAssignments.stream()
                    .filter(a -> a.getConnectionId().equals(inv.getArgument(0)))
                    .toList());
            doAnswer(inv -> {
                Long connectionId = inv.getArgument(0);
                auxPermissionAssignments.removeIf(a -> a.getConnectionId().equals(connectionId));
                return null;
            }).when(auxPermissionAssignmentRepository).deleteByConnectionId(anyLong());
            when(auxPermissionAssignmentRepository.saveAll(any())).thenAnswer(inv -> {
                List<AuxPermissionAssignment> list = new ArrayList<>();
                ((Iterable<AuxPermissionAssignment>) inv.getArgument(0)).forEach(list::add);
                for (AuxPermissionAssignment a : list) {
                    assignId(a, AUX_ID_SEQ.incrementAndGet());
                }
                auxPermissionAssignments.addAll(list);
                return list;
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
            when(userRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(usersById.get(inv.getArgument(0))));
            when(userRepository.findByEmail(any())).thenAnswer(inv -> Optional.ofNullable(usersByEmail.get(inv.getArgument(0))));
            when(groupRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(groupsById.get(inv.getArgument(0))));
            when(groupRepository.findByName(any())).thenAnswer(inv -> Optional.ofNullable(groupsByName.get(inv.getArgument(0))));
        }

        User registerUser(long id, String email) {
            User user = new User(email, "hash", null, null, Instant.now(), null);
            try {
                assignId(user, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            usersById.put(id, user);
            usersByEmail.put(email, user);
            return user;
        }

        Group registerGroup(long id, String name) {
            Group group = new Group(name, Instant.now());
            try {
                assignId(group, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            groupsById.put(id, group);
            groupsByName.put(name, group);
            return group;
        }

        PermissionAssignmentService newService() {
            return new PermissionAssignmentService(
                    permissionAssignmentRepository, auxPermissionAssignmentRepository,
                    schemaTableRepository, schemaColumnRepository, userRepository, groupRepository,
                    mock(AuditLogService.class));
        }
    }

}