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

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.permission.AuxPermissionAssignmentRepository;
import cherry.mastermeister.permission.PermissionAssignmentRepository;
import cherry.mastermeister.permission.PrincipalType;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            PermissionAssignmentRepository permissionAssignmentRepository,
            AuxPermissionAssignmentRepository auxPermissionAssignmentRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.permissionAssignmentRepository = permissionAssignmentRepository;
        this.auxPermissionAssignmentRepository = auxPermissionAssignmentRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long createGroup(Long adminUserId, String name) {
        if (groupRepository.findByName(name).isPresent()) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                    Result.FAILURE, name, "Group creation failed: name already exists");
            throw new ValidationException("Group name already exists: " + name);
        }
        Instant now = Instant.now();
        Group group = groupRepository.save(new Group(name, now));
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                Result.SUCCESS, name, "Group created: id=" + group.getId());
        eventPublisher.publishEvent(new GroupChangedEvent(group.getId()));
        return group.getId();
    }

    @Transactional
    public void renameGroup(Long adminUserId, Long groupId, String newName) {
        Group group = requireGroup(groupId);
        if (!group.getName().equals(newName) && groupRepository.findByName(newName).isPresent()) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                    Result.FAILURE, newName, "Group rename failed: name already exists");
            throw new ValidationException("Group name already exists: " + newName);
        }
        group.rename(newName);
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                Result.SUCCESS, newName, "Group renamed: id=" + groupId);
        eventPublisher.publishEvent(new GroupChangedEvent(groupId));
    }

    @Transactional
    public void deleteGroup(Long adminUserId, Long groupId) {
        Group group = requireGroup(groupId);
        groupMemberRepository.deleteByGroupId(groupId);
        permissionAssignmentRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, groupId);
        auxPermissionAssignmentRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, groupId);
        groupRepository.delete(group);
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                Result.SUCCESS, group.getName(), "Group deleted: id=" + groupId);
        eventPublisher.publishEvent(new GroupChangedEvent(groupId));
    }

    @Transactional
    public void addUserToGroup(Long adminUserId, Long groupId, Long userId) {
        Group group = requireGroup(groupId);
        if (!userRepository.existsById(userId)) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                    Result.FAILURE, group.getName(), "Add user to group failed: user not found: userId=" + userId);
            throw new EntityNotFoundException("User not found: id=" + userId);
        }
        if (groupMemberRepository.findByGroupIdAndUserId(groupId, userId).isPresent()) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                    Result.FAILURE, group.getName(), "Add user to group failed: already a member: userId=" + userId);
            throw new ValidationException("User is already a member of the group: userId=" + userId);
        }
        groupMemberRepository.save(new GroupMember(groupId, userId, Instant.now()));
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                Result.SUCCESS, group.getName(), "User added to group: userId=" + userId);
        eventPublisher.publishEvent(new GroupChangedEvent(groupId));
    }

    @Transactional
    public void removeUserFromGroup(Long adminUserId, Long groupId, Long userId) {
        Group group = requireGroup(groupId);
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (member == null) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                    Result.FAILURE, group.getName(), "Remove user from group failed: not a member: userId=" + userId);
            throw new EntityNotFoundException("GroupMember not found: groupId=" + groupId + ", userId=" + userId);
        }
        groupMemberRepository.delete(member);
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.GROUP_CHANGED, adminUserId, null,
                Result.SUCCESS, group.getName(), "User removed from group: userId=" + userId);
        eventPublisher.publishEvent(new GroupChangedEvent(groupId));
    }

    public List<GroupSummary> listGroups() {
        return groupRepository.findAll().stream()
                .map(g -> new GroupSummary(g.getId(), g.getName(), g.getCreatedAt()))
                .toList();
    }

    public List<UserSummary> listGroupMembers(Long groupId) {
        requireGroup(groupId);
        return groupMemberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUserId)
                .map(this::requireUser)
                .map(u -> new UserSummary(u.getId(), u.getEmail()))
                .toList();
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: id=" + groupId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: id=" + userId));
    }

}