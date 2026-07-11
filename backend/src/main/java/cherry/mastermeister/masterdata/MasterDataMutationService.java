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

package cherry.mastermeister.masterdata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.SchemaResolutionMode;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.PermissionDeniedException;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.schema.ColumnDetail;
import cherry.mastermeister.schema.SchemaQueryService;
import cherry.mastermeister.schema.TableDetail;

@Service
public class MasterDataMutationService {

    private final SchemaQueryService schemaQueryService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final ConnectionPoolRegistry connectionPoolRegistry;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final AuditLogService auditLogService;
    private final Duration queryTimeout;
    private final int maxMutationBatchSize;

    public MasterDataMutationService(
            SchemaQueryService schemaQueryService,
            EffectivePermissionResolver effectivePermissionResolver,
            ConnectionPoolRegistry connectionPoolRegistry,
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            AuditLogService auditLogService,
            @Value("${mm.app.master-data.query-timeout:30s}") Duration queryTimeout,
            @Value("${mm.app.master-data.max-mutation-batch-size:500}") int maxMutationBatchSize
    ) {
        this.schemaQueryService = schemaQueryService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.connectionPoolRegistry = connectionPoolRegistry;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.auditLogService = auditLogService;
        this.queryTimeout = queryTimeout;
        this.maxMutationBatchSize = maxMutationBatchSize;
    }

    public MutationResult applyChanges(
            Long userId, Long connectionId, String schema, String table, MutationRequest request
    ) {
        String targetDescription = schema + "." + table;
        int totalCount = request.creates().size() + request.updates().size() + request.deletes().size();
        if (totalCount > maxMutationBatchSize) {
            String errorMessage = "Mutation batch size exceeds limit: " + totalCount + " > " + maxMutationBatchSize;
            auditLogService.record(
                    EventCategory.DATA_ACCESS, EventType.MASTER_DATA_MUTATION, userId, connectionId, Result.FAILURE,
                    targetDescription, errorMessage);
            throw new ValidationException(errorMessage);
        }

        try {
            validatePermissions(userId, connectionId, schema, table, request);
        } catch (PermissionDeniedException | ValidationException e) {
            auditLogService.record(
                    EventCategory.DATA_ACCESS, EventType.MASTER_DATA_MUTATION, userId, connectionId, Result.FAILURE,
                    targetDescription, e.getMessage());
            throw e;
        }

        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));
        DialectStrategy dialect = dialectStrategyFactory.resolve(connection.getRdbmsType());
        String qualifiedTable = buildQualifiedTableName(dialect, schema, table);
        List<String> primaryKeyColumns = primaryKeyColumnNames(connectionId, schema, table);

        NamedParameterJdbcTemplate jdbcTemplate = connectionPoolRegistry.getJdbcTemplate(connectionId);
        TransactionTemplate transactionTemplate = connectionPoolRegistry.getTransactionTemplate(connectionId);

        try {
            MutationResult result = transactionTemplate.execute(status -> {
                ((JdbcTemplate) jdbcTemplate.getJdbcOperations()).setQueryTimeout((int) queryTimeout.toSeconds());
                int createdCount = 0;
                for (RecordCreate create : request.creates()) {
                    jdbcTemplate.update(buildInsertSql(dialect, qualifiedTable, create), toParams(create.values()));
                    createdCount++;
                }
                int updatedCount = 0;
                for (RecordUpdate update : request.updates()) {
                    jdbcTemplate.update(
                            buildUpdateSql(dialect, qualifiedTable, update, primaryKeyColumns),
                            toUpdateParams(update));
                    updatedCount++;
                }
                int deletedCount = 0;
                for (RecordDelete delete : request.deletes()) {
                    jdbcTemplate.update(
                            buildDeleteSql(dialect, qualifiedTable, primaryKeyColumns),
                            toParams(delete.primaryKeyValues()));
                    deletedCount++;
                }
                return new MutationResult(true, createdCount, updatedCount, deletedCount, null);
            });

            auditLogService.record(
                    EventCategory.DATA_ACCESS, EventType.MASTER_DATA_MUTATION, userId, connectionId, Result.SUCCESS,
                    targetDescription,
                    "created=" + result.createdCount() + ", updated=" + result.updatedCount()
                            + ", deleted=" + result.deletedCount());
            return result;
        } catch (DataAccessException e) {
            String errorMessage = e.getMostSpecificCause().getMessage();
            auditLogService.record(
                    EventCategory.DATA_ACCESS, EventType.MASTER_DATA_MUTATION, userId, connectionId, Result.FAILURE,
                    targetDescription, errorMessage);
            return new MutationResult(false, 0, 0, 0, errorMessage);
        }
    }

    private void validatePermissions(
            Long userId, Long connectionId, String schema, String table, MutationRequest request
    ) {
        if (!request.creates().isEmpty()
                && !effectivePermissionResolver.canCreate(userId, connectionId, schema, table)) {
            throw new PermissionDeniedException("No create permission on table: " + schema + "." + table);
        }
        if (!request.deletes().isEmpty()
                && !effectivePermissionResolver.canDelete(userId, connectionId, schema, table)) {
            throw new PermissionDeniedException("No delete permission on table: " + schema + "." + table);
        }
        if (!request.updates().isEmpty()) {
            Map<String, Permission> columnPermissions = effectivePermissionResolver
                    .resolveEffectiveColumnPermissions(userId, connectionId, schema, table);
            List<String> primaryKeyColumns = primaryKeyColumnNames(connectionId, schema, table);
            if (primaryKeyColumns.isEmpty()) {
                throw new ValidationException("Table has no primary key, updates are not allowed: " + schema + "." + table);
            }
            for (RecordUpdate update : request.updates()) {
                for (String columnName : update.changedValues().keySet()) {
                    Permission permission = columnPermissions.getOrDefault(columnName, Permission.NONE);
                    if (permission.compareTo(Permission.UPDATE) < 0) {
                        throw new PermissionDeniedException("No update permission on column: " + columnName);
                    }
                }
            }
        }
    }

    private List<String> primaryKeyColumnNames(Long connectionId, String schema, String table) {
        TableDetail tableDetail = schemaQueryService.getTableDetail(connectionId, schema, table);
        List<String> primaryKeyColumns = new ArrayList<>();
        for (ColumnDetail column : tableDetail.columns()) {
            if (column.primaryKeySequence() != null) {
                primaryKeyColumns.add(column.columnName());
            }
        }
        return primaryKeyColumns;
    }

    private String buildQualifiedTableName(DialectStrategy dialect, String schema, String table) {
        if (dialect.getSchemaResolutionMode() == SchemaResolutionMode.CATALOG_BASED) {
            return dialect.quoteIdentifier(table);
        }
        return dialect.quoteIdentifier(schema) + "." + dialect.quoteIdentifier(table);
    }

    private String buildInsertSql(DialectStrategy dialect, String qualifiedTable, RecordCreate create) {
        List<String> columnNames = create.values().keySet().stream().toList();
        String columns = columnNames.stream().map(dialect::quoteIdentifier)
                .reduce((a, b) -> a + ", " + b).orElse("");
        String placeholders = columnNames.stream().map(name -> ":" + name)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return "INSERT INTO " + qualifiedTable + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private String buildUpdateSql(
            DialectStrategy dialect, String qualifiedTable, RecordUpdate update, List<String> primaryKeyColumns
    ) {
        String setClause = update.changedValues().keySet().stream()
                .map(name -> dialect.quoteIdentifier(name) + " = :set_" + name)
                .reduce((a, b) -> a + ", " + b).orElse("");
        String whereClause = primaryKeyColumns.stream()
                .map(name -> dialect.quoteIdentifier(name) + " = :pk_" + name)
                .reduce((a, b) -> a + " AND " + b).orElse("");
        return "UPDATE " + qualifiedTable + " SET " + setClause + " WHERE " + whereClause;
    }

    private String buildDeleteSql(DialectStrategy dialect, String qualifiedTable, List<String> primaryKeyColumns) {
        String whereClause = primaryKeyColumns.stream()
                .map(name -> dialect.quoteIdentifier(name) + " = :" + name)
                .reduce((a, b) -> a + " AND " + b).orElse("");
        return "DELETE FROM " + qualifiedTable + " WHERE " + whereClause;
    }

    private MapSqlParameterSource toParams(Map<String, Object> values) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        values.forEach(params::addValue);
        return params;
    }

    private MapSqlParameterSource toUpdateParams(RecordUpdate update) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        update.changedValues().forEach((name, value) -> params.addValue("set_" + name, value));
        update.primaryKeyValues().forEach((name, value) -> params.addValue("pk_" + name, value));
        return params;
    }

}