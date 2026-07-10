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

package cherry.mastermeister.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationEventPublisher;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.permission.AuxPermissionAssignment;
import cherry.mastermeister.permission.AuxPermissionAssignmentRepository;
import cherry.mastermeister.permission.AuxPermissionType;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.permission.PermissionAssignment;
import cherry.mastermeister.permission.PermissionAssignmentRepository;
import cherry.mastermeister.permission.PrincipalType;
import cherry.mastermeister.userregistration.UserRepository;

/**
 * P1・P2（business-logic-model.md）を検証するプロパティテスト。
 */
class GroupServiceTest {

    private static final AtomicLong GROUP_ID_SEQ = new AtomicLong();
    private static final AtomicLong MEMBER_ID_SEQ = new AtomicLong();

    // P1: deleteGroup実行後、対象groupIdを参照するGroupMember/PermissionAssignment/
    // AuxPermissionAssignmentが1件も残らないことを検証する（付随データ件数をjqwikで振る）。
    // 併せて、無関係の他グループの行が誤って削除されないことも確認する。
    @Property(tries = 20)
    void deleteGroupRemovesAllReferencingRows(
            @ForAll("counts") int memberCount,
            @ForAll("counts") int permissionCount,
            @ForAll("counts") int auxPermissionCount
    ) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long groupId = GROUP_ID_SEQ.incrementAndGet();
        when(repos.groupRepository.findById(groupId)).thenReturn(Optional.of(newGroup(groupId, "group-" + groupId)));

        for (int i = 0; i < memberCount; i++) {
            repos.groupMembers.add(new GroupMember(groupId, (long) (i + 1), Instant.now()));
        }
        for (int i = 0; i < permissionCount; i++) {
            repos.permissionAssignments.add(new PermissionAssignment(
                    PrincipalType.GROUP, groupId, 1L, "SCHEMA", "T" + i, null, Permission.READ, Instant.now()));
        }
        for (int i = 0; i < auxPermissionCount; i++) {
            repos.auxPermissionAssignments.add(new AuxPermissionAssignment(
                    PrincipalType.GROUP, groupId, 1L, "SCHEMA", "T" + i, AuxPermissionType.CREATE, true, Instant.now()));
        }
        Long otherGroupId = groupId + 100_000L;
        repos.groupMembers.add(new GroupMember(otherGroupId, 999L, Instant.now()));
        repos.permissionAssignments.add(new PermissionAssignment(
                PrincipalType.GROUP, otherGroupId, 1L, "SCHEMA", "T", null, Permission.READ, Instant.now()));

        GroupService service = repos.newService();

        service.deleteGroup(1L, groupId);

        assertThat(repos.groupMembers).noneMatch(m -> m.getGroupId().equals(groupId));
        assertThat(repos.permissionAssignments).noneMatch(
                p -> p.getPrincipalType() == PrincipalType.GROUP && p.getPrincipalId().equals(groupId));
        assertThat(repos.auxPermissionAssignments).noneMatch(
                p -> p.getPrincipalType() == PrincipalType.GROUP && p.getPrincipalId().equals(groupId));
        assertThat(repos.groupMembers).anyMatch(m -> m.getGroupId().equals(otherGroupId));
        assertThat(repos.permissionAssignments).anyMatch(p -> p.getPrincipalId().equals(otherGroupId));
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(0, 5);
    }

    // P2: 既に所属済みの(groupId, userId)への2回目のaddUserToGroupは常に例外となり、
    // GroupMemberの行数は変化しない。
    @Property(tries = 20)
    void addUserToGroupRejectsDuplicateMembership(@ForAll("userIds") Long userId) throws Exception {
        FakeRepositories repos = new FakeRepositories();
        Long groupId = GROUP_ID_SEQ.incrementAndGet();
        when(repos.groupRepository.findById(groupId)).thenReturn(Optional.of(newGroup(groupId, "group-" + groupId)));
        when(repos.userRepository.existsById(userId)).thenReturn(true);

        GroupService service = repos.newService();

        service.addUserToGroup(1L, groupId, userId);
        int countAfterFirst = repos.groupMembers.size();

        assertThatThrownBy(() -> service.addUserToGroup(1L, groupId, userId))
                .isInstanceOf(ValidationException.class);

        assertThat(repos.groupMembers).hasSize(countAfterFirst);
    }

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    private static Group newGroup(Long id, String name) throws Exception {
        Group group = new Group(name, Instant.now());
        assignId(group, id);
        return group;
    }

    private static void assignId(Object entity, long id) throws NoSuchFieldException, IllegalAccessException {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static final class FakeRepositories {
        final GroupRepository groupRepository = mock(GroupRepository.class);
        final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final PermissionAssignmentRepository permissionAssignmentRepository = mock(PermissionAssignmentRepository.class);
        final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository = mock(AuxPermissionAssignmentRepository.class);
        final List<GroupMember> groupMembers = new ArrayList<>();
        final List<PermissionAssignment> permissionAssignments = new ArrayList<>();
        final List<AuxPermissionAssignment> auxPermissionAssignments = new ArrayList<>();

        FakeRepositories() {
            when(groupMemberRepository.findByGroupIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> groupMembers.stream()
                    .filter(m -> m.getGroupId().equals(inv.getArgument(0)) && m.getUserId().equals(inv.getArgument(1)))
                    .findFirst());
            when(groupMemberRepository.save(any())).thenAnswer(inv -> {
                GroupMember member = inv.getArgument(0);
                assignId(member, MEMBER_ID_SEQ.incrementAndGet());
                groupMembers.add(member);
                return member;
            });
            doAnswer(inv -> {
                Long groupId = inv.getArgument(0);
                groupMembers.removeIf(m -> m.getGroupId().equals(groupId));
                return null;
            }).when(groupMemberRepository).deleteByGroupId(anyLong());
            doAnswer(inv -> {
                PrincipalType type = inv.getArgument(0);
                Long principalId = inv.getArgument(1);
                permissionAssignments.removeIf(p -> p.getPrincipalType() == type && p.getPrincipalId().equals(principalId));
                return null;
            }).when(permissionAssignmentRepository).deleteByPrincipalTypeAndPrincipalId(any(), anyLong());
            doAnswer(inv -> {
                PrincipalType type = inv.getArgument(0);
                Long principalId = inv.getArgument(1);
                auxPermissionAssignments.removeIf(p -> p.getPrincipalType() == type && p.getPrincipalId().equals(principalId));
                return null;
            }).when(auxPermissionAssignmentRepository).deleteByPrincipalTypeAndPrincipalId(any(), anyLong());
        }

        GroupService newService() {
            return new GroupService(
                    groupRepository, groupMemberRepository, userRepository,
                    permissionAssignmentRepository, auxPermissionAssignmentRepository,
                    mock(AuditLogService.class), mock(ApplicationEventPublisher.class));
        }
    }

}