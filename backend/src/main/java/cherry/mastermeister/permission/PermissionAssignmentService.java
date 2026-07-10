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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.group.Group;
import cherry.mastermeister.group.GroupRepository;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;
import cherry.mastermeister.userregistration.User;
import cherry.mastermeister.userregistration.UserRepository;

@Service
public class PermissionAssignmentService {

    private static final String[] EFFECTIVE_PERMISSION_CACHE_NAMES = {
            "effectivePermissions.table",
            "effectivePermissions.columns",
            "effectivePermissions.canCreate",
            "effectivePermissions.canDelete",
            "effectivePermissions.schemas",
            "effectivePermissions.tables"
    };

    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository;
    private final SchemaTableRepository schemaTableRepository;
    private final SchemaColumnRepository schemaColumnRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditLogService auditLogService;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public PermissionAssignmentService(
            PermissionAssignmentRepository permissionAssignmentRepository,
            AuxPermissionAssignmentRepository auxPermissionAssignmentRepository,
            SchemaTableRepository schemaTableRepository,
            SchemaColumnRepository schemaColumnRepository,
            UserRepository userRepository,
            GroupRepository groupRepository,
            AuditLogService auditLogService
    ) {
        this.permissionAssignmentRepository = permissionAssignmentRepository;
        this.auxPermissionAssignmentRepository = auxPermissionAssignmentRepository;
        this.schemaTableRepository = schemaTableRepository;
        this.schemaColumnRepository = schemaColumnRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "effectivePermissions.table", "effectivePermissions.columns",
            "effectivePermissions.canCreate", "effectivePermissions.canDelete",
            "effectivePermissions.schemas", "effectivePermissions.tables"
    }, allEntries = true)
    public void setPermission(
            Long adminUserId, PrincipalRef principal, Long connectionId, String schema,
            Optional<String> table, Optional<String> column, Permission permission
    ) {
        String target = describeTarget(principal, schema, table, column);

        if (column.isPresent() && table.isEmpty()) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                    Result.FAILURE, target, "Set permission failed: column specified without table");
            throw new ValidationException("column requires table to be specified: " + target);
        }
        if (!referenceExists(connectionId, schema, table.orElse(null), column.orElse(null))) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                    Result.FAILURE, target, "Set permission failed: table/column not found");
            throw new ValidationException("Referenced table/column not found: " + target);
        }
        if (!principalExists(principal)) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                    Result.FAILURE, target, "Set permission failed: principal not found");
            throw new ValidationException("Principal not found: " + target);
        }

        Instant now = Instant.now();
        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                        principal.principalType(), principal.principalId(), connectionId,
                        schema, table.orElse(null), column.orElse(null))
                .orElse(null);
        if (assignment == null) {
            permissionAssignmentRepository.save(new PermissionAssignment(
                    principal.principalType(), principal.principalId(), connectionId,
                    schema, table.orElse(null), column.orElse(null), permission, now));
        } else {
            assignment.update(permission, now);
        }

        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                Result.SUCCESS, target, "Permission set: " + permission);
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "effectivePermissions.table", "effectivePermissions.columns",
            "effectivePermissions.canCreate", "effectivePermissions.canDelete",
            "effectivePermissions.schemas", "effectivePermissions.tables"
    }, allEntries = true)
    public void setAuxPermission(
            Long adminUserId, PrincipalRef principal, Long connectionId, String schema,
            Optional<String> table, AuxPermissionType auxType, boolean granted
    ) {
        String target = describeTarget(principal, schema, table, Optional.empty()) + " " + auxType;

        if (!referenceExists(connectionId, schema, table.orElse(null), null)) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                    Result.FAILURE, target, "Set aux permission failed: table not found");
            throw new ValidationException("Referenced table not found: " + target);
        }
        if (!principalExists(principal)) {
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                    Result.FAILURE, target, "Set aux permission failed: principal not found");
            throw new ValidationException("Principal not found: " + target);
        }

        Instant now = Instant.now();
        AuxPermissionAssignment assignment = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        principal.principalType(), principal.principalId(), connectionId,
                        schema, table.orElse(null), auxType)
                .orElse(null);
        if (assignment == null) {
            auxPermissionAssignmentRepository.save(new AuxPermissionAssignment(
                    principal.principalType(), principal.principalId(), connectionId,
                    schema, table.orElse(null), auxType, granted, now));
        } else {
            assignment.update(granted, now);
        }

        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.PERMISSION_CHANGED, adminUserId, connectionId,
                Result.SUCCESS, target, "Aux permission set: " + auxType + "=" + granted);
    }

    public byte[] exportPermissionsAsYaml(Long adminUserId, Long connectionId) {
        List<PermissionAssignment> permissions = permissionAssignmentRepository.findByConnectionId(connectionId);
        List<AuxPermissionAssignment> auxPermissions = auxPermissionAssignmentRepository.findByConnectionId(connectionId);

        Map<PrincipalRef, List<PermissionAssignment>> permissionsByPrincipal = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> new PrincipalRef(p.getPrincipalType(), p.getPrincipalId()),
                        LinkedHashMap::new, Collectors.toList()));
        Map<PrincipalRef, List<AuxPermissionAssignment>> auxPermissionsByPrincipal = auxPermissions.stream()
                .collect(Collectors.groupingBy(
                        p -> new PrincipalRef(p.getPrincipalType(), p.getPrincipalId()),
                        LinkedHashMap::new, Collectors.toList()));

        Set<PrincipalRef> principalRefs = new LinkedHashSet<>();
        principalRefs.addAll(permissionsByPrincipal.keySet());
        principalRefs.addAll(auxPermissionsByPrincipal.keySet());

        List<PrincipalYaml> principalYamlList = new ArrayList<>();
        for (PrincipalRef ref : principalRefs) {
            PrincipalYaml principalYaml = new PrincipalYaml();
            principalYaml.setType(ref.principalType().name());
            if (ref.principalType() == PrincipalType.USER) {
                principalYaml.setEmail(requireUser(ref.principalId()).getEmail());
            } else {
                principalYaml.setName(requireGroup(ref.principalId()).getName());
            }
            principalYaml.setPermissions(permissionsByPrincipal
                    .getOrDefault(ref, List.of()).stream()
                    .map(PermissionAssignmentService::toPermissionEntryYaml)
                    .toList());
            principalYaml.setAuxPermissions(auxPermissionsByPrincipal
                    .getOrDefault(ref, List.of()).stream()
                    .map(PermissionAssignmentService::toAuxPermissionEntryYaml)
                    .toList());
            principalYamlList.add(principalYaml);
        }

        PermissionYamlDocument document = new PermissionYamlDocument();
        document.setPrincipals(principalYamlList);

        byte[] content;
        try {
            content = yamlMapper.writeValueAsBytes(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize permissions to YAML", e);
        }

        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.PERMISSION_YAML_EXPORTED, adminUserId, connectionId,
                Result.SUCCESS, "connectionId=" + connectionId, "Permissions exported as YAML");
        return content;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "effectivePermissions.table", "effectivePermissions.columns",
            "effectivePermissions.canCreate", "effectivePermissions.canDelete",
            "effectivePermissions.schemas", "effectivePermissions.tables"
    }, allEntries = true)
    public ImportResult importPermissionsFromYaml(Long adminUserId, Long connectionId, byte[] yamlContent) {
        try {
            PermissionYamlDocument document = parseYaml(yamlContent);
            if (document.getPrincipals() == null) {
                throw new PermissionYamlFormatException("Missing required field: principals");
            }

            List<PermissionAssignment> newPermissions = new ArrayList<>();
            List<AuxPermissionAssignment> newAuxPermissions = new ArrayList<>();
            Set<String> permissionKeys = new HashSet<>();
            Set<String> auxPermissionKeys = new HashSet<>();
            Instant now = Instant.now();

            for (PrincipalYaml principalYaml : document.getPrincipals()) {
                String typeValue = principalYaml.getType();
                if (typeValue == null || typeValue.isBlank()) {
                    throw new PermissionYamlFormatException("Missing required field: type");
                }
                PrincipalType principalType;
                try {
                    principalType = PrincipalType.valueOf(typeValue);
                } catch (IllegalArgumentException e) {
                    throw new PermissionYamlFormatException("Invalid type value: " + typeValue);
                }

                Long principalId;
                String principalKey;
                if (principalType == PrincipalType.USER) {
                    String email = principalYaml.getEmail();
                    if (email == null || email.isBlank()) {
                        throw new PermissionYamlFormatException("Missing required field: email");
                    }
                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new PermissionYamlFormatException("User not found: email=" + email));
                    principalId = user.getId();
                    principalKey = "USER:" + email;
                } else {
                    String name = principalYaml.getName();
                    if (name == null || name.isBlank()) {
                        throw new PermissionYamlFormatException("Missing required field: name");
                    }
                    Group group = groupRepository.findByName(name)
                            .orElseThrow(() -> new PermissionYamlFormatException("Group not found: name=" + name));
                    principalId = group.getId();
                    principalKey = "GROUP:" + name;
                }

                if (principalYaml.getPermissions() != null) {
                    for (PermissionEntryYaml entry : principalYaml.getPermissions()) {
                        String schema = entry.getSchema();
                        if (schema == null || schema.isBlank()) {
                            throw new PermissionYamlFormatException("Missing required field: schema");
                        }
                        String permissionValue = entry.getPermission();
                        if (permissionValue == null || permissionValue.isBlank()) {
                            throw new PermissionYamlFormatException("Missing required field: permission");
                        }
                        Permission permission;
                        try {
                            permission = Permission.valueOf(permissionValue);
                        } catch (IllegalArgumentException e) {
                            throw new PermissionYamlFormatException("Invalid permission value: " + permissionValue);
                        }
                        String table = blankToNull(entry.getTable());
                        String column = blankToNull(entry.getColumn());
                        if (column != null && table == null) {
                            throw new PermissionYamlFormatException(
                                    "column specified without table: schema=" + schema + ", column=" + column);
                        }
                        if (!referenceExists(connectionId, schema, table, column)) {
                            throw new PermissionYamlFormatException(
                                    "Referenced table/column not found: schema=" + schema
                                            + ", table=" + table + ", column=" + column);
                        }
                        String key = principalKey + "|" + schema + "|" + table + "|" + column;
                        if (!permissionKeys.add(key)) {
                            throw new PermissionYamlFormatException("Duplicate permission entry: " + key);
                        }
                        newPermissions.add(new PermissionAssignment(
                                principalType, principalId, connectionId, schema, table, column, permission, now));
                    }
                }

                if (principalYaml.getAuxPermissions() != null) {
                    for (AuxPermissionEntryYaml entry : principalYaml.getAuxPermissions()) {
                        String schema = entry.getSchema();
                        if (schema == null || schema.isBlank()) {
                            throw new PermissionYamlFormatException("Missing required field: schema");
                        }
                        String typeValue2 = entry.getType();
                        if (typeValue2 == null || typeValue2.isBlank()) {
                            throw new PermissionYamlFormatException("Missing required field: type");
                        }
                        AuxPermissionType auxType;
                        try {
                            auxType = AuxPermissionType.valueOf(typeValue2);
                        } catch (IllegalArgumentException e) {
                            throw new PermissionYamlFormatException("Invalid type value: " + typeValue2);
                        }
                        if (entry.getGranted() == null) {
                            throw new PermissionYamlFormatException("Missing required field: granted");
                        }
                        String table = blankToNull(entry.getTable());
                        if (!referenceExists(connectionId, schema, table, null)) {
                            throw new PermissionYamlFormatException(
                                    "Referenced table not found: schema=" + schema + ", table=" + table);
                        }
                        String key = principalKey + "|" + schema + "|" + table + "|" + auxType;
                        if (!auxPermissionKeys.add(key)) {
                            throw new PermissionYamlFormatException("Duplicate aux permission entry: " + key);
                        }
                        newAuxPermissions.add(new AuxPermissionAssignment(
                                principalType, principalId, connectionId, schema, table, auxType,
                                entry.getGranted(), now));
                    }
                }
            }

            permissionAssignmentRepository.deleteByConnectionId(connectionId);
            auxPermissionAssignmentRepository.deleteByConnectionId(connectionId);
            permissionAssignmentRepository.saveAll(newPermissions);
            auxPermissionAssignmentRepository.saveAll(newAuxPermissions);

            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_YAML_IMPORTED, adminUserId, connectionId,
                    Result.SUCCESS, "connectionId=" + connectionId,
                    "Permissions imported: principals=" + document.getPrincipals().size());
            return new ImportResult(true, "Import succeeded.");
        } catch (PermissionYamlFormatException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.PERMISSION_YAML_IMPORTED, adminUserId, connectionId,
                    Result.FAILURE, "connectionId=" + connectionId,
                    "Permission import failed: " + e.getMessage());
            return new ImportResult(false, e.getMessage());
        }
    }

    private PermissionYamlDocument parseYaml(byte[] yamlContent) {
        try {
            return yamlMapper.readValue(yamlContent, PermissionYamlDocument.class);
        } catch (IOException e) {
            throw new PermissionYamlFormatException("YAML syntax error: " + e.getMessage(), e);
        }
    }

    private boolean referenceExists(Long connectionId, String schema, String table, String column) {
        if (table == null) {
            return schemaTableRepository.existsByConnectionIdAndSchemaName(connectionId, schema);
        }
        Optional<SchemaTable> schemaTable = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(connectionId, schema, table);
        if (schemaTable.isEmpty()) {
            return false;
        }
        if (column == null) {
            return true;
        }
        return schemaColumnRepository.findByTableIdAndColumnName(schemaTable.get().getId(), column).isPresent();
    }

    private boolean principalExists(PrincipalRef principal) {
        return switch (principal.principalType()) {
            case USER -> userRepository.existsById(principal.principalId());
            case GROUP -> groupRepository.existsById(principal.principalId());
        };
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: id=" + userId));
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found: id=" + groupId));
    }

    private static String describeTarget(
            PrincipalRef principal, String schema, Optional<String> table, Optional<String> column
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(principal.principalType()).append(':').append(principal.principalId());
        sb.append(' ').append(schema);
        table.ifPresent(t -> sb.append('.').append(t));
        column.ifPresent(c -> sb.append('.').append(c));
        return sb.toString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static PermissionEntryYaml toPermissionEntryYaml(PermissionAssignment assignment) {
        PermissionEntryYaml entry = new PermissionEntryYaml();
        entry.setSchema(assignment.getSchemaName());
        entry.setTable(assignment.getTableName());
        entry.setColumn(assignment.getColumnName());
        entry.setPermission(assignment.getPermission().name());
        return entry;
    }

    private static AuxPermissionEntryYaml toAuxPermissionEntryYaml(AuxPermissionAssignment assignment) {
        AuxPermissionEntryYaml entry = new AuxPermissionEntryYaml();
        entry.setSchema(assignment.getSchemaName());
        entry.setTable(assignment.getTableName());
        entry.setType(assignment.getAuxType().name());
        entry.setGranted(assignment.isGranted());
        return entry;
    }

}