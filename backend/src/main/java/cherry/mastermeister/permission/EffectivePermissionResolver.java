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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import cherry.mastermeister.group.GroupMember;
import cherry.mastermeister.group.GroupMemberRepository;
import cherry.mastermeister.schema.SchemaColumn;
import cherry.mastermeister.schema.SchemaColumnRepository;
import cherry.mastermeister.schema.SchemaTable;
import cherry.mastermeister.schema.SchemaTableRepository;

@Component
public class EffectivePermissionResolver {

    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final AuxPermissionAssignmentRepository auxPermissionAssignmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SchemaTableRepository schemaTableRepository;
    private final SchemaColumnRepository schemaColumnRepository;

    public EffectivePermissionResolver(
            PermissionAssignmentRepository permissionAssignmentRepository,
            AuxPermissionAssignmentRepository auxPermissionAssignmentRepository,
            GroupMemberRepository groupMemberRepository,
            SchemaTableRepository schemaTableRepository,
            SchemaColumnRepository schemaColumnRepository
    ) {
        this.permissionAssignmentRepository = permissionAssignmentRepository;
        this.auxPermissionAssignmentRepository = auxPermissionAssignmentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.schemaTableRepository = schemaTableRepository;
        this.schemaColumnRepository = schemaColumnRepository;
    }

    @Cacheable(cacheNames = "effectivePermissions.table")
    public Permission resolveEffectiveTablePermission(Long userId, Long connectionId, String schema, String table) {
        return resolveMainPermission(userId, connectionId, schema, table, null);
    }

    @Cacheable(cacheNames = "effectivePermissions.columns")
    public Map<String, Permission> resolveEffectiveColumnPermissions(
            Long userId, Long connectionId, String schema, String table
    ) {
        Optional<SchemaTable> schemaTable = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(connectionId, schema, table);
        if (schemaTable.isEmpty()) {
            return Map.of();
        }
        List<SchemaColumn> columns = schemaColumnRepository.findByTableIdAndStaleFalse(schemaTable.get().getId())
                .stream()
                .sorted(Comparator.comparing(SchemaColumn::getOrdinalPosition))
                .toList();
        Map<String, Permission> result = new LinkedHashMap<>();
        for (SchemaColumn column : columns) {
            result.put(column.getColumnName(),
                    resolveMainPermission(userId, connectionId, schema, table, column.getColumnName()));
        }
        return result;
    }

    @Cacheable(cacheNames = "effectivePermissions.canCreate")
    public boolean canCreate(Long userId, Long connectionId, String schema, String table) {
        Optional<SchemaTable> schemaTable = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(connectionId, schema, table);
        if (schemaTable.isEmpty()) {
            return false;
        }
        if (!resolveAuxPermission(userId, connectionId, schema, table, AuxPermissionType.CREATE)) {
            return false;
        }
        List<SchemaColumn> primaryKeyColumns = primaryKeyColumns(schemaTable.get().getId());
        if (primaryKeyColumns.isEmpty()) {
            return true;
        }
        return primaryKeyColumns.stream().allMatch(column ->
                resolveMainPermission(userId, connectionId, schema, table, column.getColumnName())
                        .compareTo(Permission.UPDATE) >= 0);
    }

    @Cacheable(cacheNames = "effectivePermissions.canDelete")
    public boolean canDelete(Long userId, Long connectionId, String schema, String table) {
        Optional<SchemaTable> schemaTable = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(connectionId, schema, table);
        if (schemaTable.isEmpty()) {
            return false;
        }
        if (!resolveAuxPermission(userId, connectionId, schema, table, AuxPermissionType.DELETE)) {
            return false;
        }
        List<SchemaColumn> primaryKeyColumns = primaryKeyColumns(schemaTable.get().getId());
        if (primaryKeyColumns.isEmpty()) {
            return false;
        }
        return primaryKeyColumns.stream().allMatch(column ->
                resolveMainPermission(userId, connectionId, schema, table, column.getColumnName())
                        .compareTo(Permission.READ) >= 0);
    }

    @Cacheable(cacheNames = "effectivePermissions.schemas")
    public List<String> listAccessibleSchemas(Long userId, Long connectionId) {
        List<SchemaTable> tables = schemaTableRepository.findByConnectionIdAndStaleFalse(connectionId);
        List<String> schemas = tables.stream().map(SchemaTable::getSchemaName).distinct().toList();
        List<String> accessible = new ArrayList<>();
        for (String schema : schemas) {
            boolean hasAccessibleTable = tables.stream()
                    .filter(t -> t.getSchemaName().equals(schema))
                    .anyMatch(t -> resolveMainPermission(userId, connectionId, schema, t.getTableName(), null)
                            != Permission.NONE);
            if (hasAccessibleTable) {
                accessible.add(schema);
            }
        }
        return accessible;
    }

    @Cacheable(cacheNames = "effectivePermissions.tables")
    public List<String> listAccessibleTables(Long userId, Long connectionId, String schema) {
        return schemaTableRepository.findByConnectionIdAndSchemaNameAndStaleFalse(connectionId, schema).stream()
                .filter(t -> resolveMainPermission(userId, connectionId, schema, t.getTableName(), null)
                        != Permission.NONE)
                .map(SchemaTable::getTableName)
                .toList();
    }

    private List<SchemaColumn> primaryKeyColumns(Long tableId) {
        return schemaColumnRepository.findByTableIdAndStaleFalse(tableId).stream()
                .filter(column -> column.getPrimaryKeySequence() != null)
                .sorted(Comparator.comparing(SchemaColumn::getPrimaryKeySequence))
                .toList();
    }

    private Permission resolveMainPermission(
            Long userId, Long connectionId, String schema, String table, String column
    ) {
        Optional<Permission> userExplicit = findMostSpecificPermission(
                PrincipalType.USER, userId, connectionId, schema, table, column);
        if (userExplicit.isPresent()) {
            return userExplicit.get();
        }
        Permission composed = Permission.NONE;
        for (Long groupId : groupIdsOf(userId)) {
            Permission groupResolved = findMostSpecificPermission(
                    PrincipalType.GROUP, groupId, connectionId, schema, table, column).orElse(Permission.NONE);
            composed = Permission.max(composed, groupResolved);
        }
        return composed;
    }

    private Optional<Permission> findMostSpecificPermission(
            PrincipalType principalType, Long principalId, Long connectionId, String schema, String table, String column
    ) {
        if (column != null) {
            Optional<PermissionAssignment> exact = permissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                            principalType, principalId, connectionId, schema, table, column);
            if (exact.isPresent()) {
                return Optional.of(exact.get().getPermission());
            }
        }
        if (table != null) {
            Optional<PermissionAssignment> tableLevel = permissionAssignmentRepository
                    .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                            principalType, principalId, connectionId, schema, table, null);
            if (tableLevel.isPresent()) {
                return Optional.of(tableLevel.get().getPermission());
            }
        }
        Optional<PermissionAssignment> schemaLevel = permissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                        principalType, principalId, connectionId, schema, null, null);
        return schemaLevel.map(PermissionAssignment::getPermission);
    }

    private boolean resolveAuxPermission(
            Long userId, Long connectionId, String schema, String table, AuxPermissionType auxType
    ) {
        Optional<Boolean> userExplicit = findMostSpecificAuxPermission(
                PrincipalType.USER, userId, connectionId, schema, table, auxType);
        if (userExplicit.isPresent()) {
            return userExplicit.get();
        }
        for (Long groupId : groupIdsOf(userId)) {
            boolean groupGranted = findMostSpecificAuxPermission(
                    PrincipalType.GROUP, groupId, connectionId, schema, table, auxType).orElse(false);
            if (groupGranted) {
                return true;
            }
        }
        return false;
    }

    private Optional<Boolean> findMostSpecificAuxPermission(
            PrincipalType principalType, Long principalId, Long connectionId, String schema, String table,
            AuxPermissionType auxType
    ) {
        Optional<AuxPermissionAssignment> tableLevel = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        principalType, principalId, connectionId, schema, table, auxType);
        if (tableLevel.isPresent()) {
            return Optional.of(tableLevel.get().isGranted());
        }
        Optional<AuxPermissionAssignment> schemaLevel = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        principalType, principalId, connectionId, schema, null, auxType);
        return schemaLevel.map(AuxPermissionAssignment::isGranted);
    }

    private List<Long> groupIdsOf(Long userId) {
        return groupMemberRepository.findByUserId(userId).stream().map(GroupMember::getGroupId).toList();
    }

}