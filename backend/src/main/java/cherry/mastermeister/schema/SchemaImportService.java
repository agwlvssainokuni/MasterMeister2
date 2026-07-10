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

package cherry.mastermeister.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.SchemaResolutionMode;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

@Service
public class SchemaImportService {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final SchemaTableRepository schemaTableRepository;
    private final SchemaColumnRepository schemaColumnRepository;
    private final ConnectionPoolRegistry connectionPoolRegistry;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public SchemaImportService(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            SchemaTableRepository schemaTableRepository,
            SchemaColumnRepository schemaColumnRepository,
            ConnectionPoolRegistry connectionPoolRegistry,
            DialectStrategyFactory dialectStrategyFactory,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.schemaTableRepository = schemaTableRepository;
        this.schemaColumnRepository = schemaColumnRepository;
        this.connectionPoolRegistry = connectionPoolRegistry;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SchemaImportResult importSchema(Long connectionId, Long adminUserId) {
        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: id=" + connectionId));
        DialectStrategy dialectStrategy = dialectStrategyFactory.resolve(connection.getRdbmsType());
        DataSource dataSource = connectionPoolRegistry.getDataSource(connectionId);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = connection.getDatabaseName();
            SchemaResolutionMode mode = dialectStrategy.getSchemaResolutionMode();
            List<String> schemaNames = resolveSchemaNames(metaData, catalog, mode);
            Instant now = Instant.now();
            Set<Long> seenTableIds = new HashSet<>();
            int tableCount = 0;

            for (String schemaName : schemaNames) {
                String schemaPattern = mode == SchemaResolutionMode.CATALOG_BASED ? null : schemaName;
                try (ResultSet tables = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        TableType tableType = "VIEW".equals(tables.getString("TABLE_TYPE"))
                                ? TableType.VIEW : TableType.TABLE;
                        String comment = tables.getString("REMARKS");

                        SchemaTable schemaTable = upsertTable(connectionId, schemaName, tableName, tableType, comment, now);
                        seenTableIds.add(schemaTable.getId());
                        tableCount++;

                        importColumns(metaData, catalog, schemaPattern, schemaTable, tableName, tableType, now);
                    }
                }
            }

            markStaleTables(connectionId, seenTableIds, now);

            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.SCHEMA_IMPORTED, adminUserId, connectionId,
                    Result.SUCCESS, connection.getName(),
                    "Schema import succeeded: tableCount=" + tableCount);
            eventPublisher.publishEvent(new SchemaReimportedEvent(connectionId));
            return new SchemaImportResult(true, tableCount, "Schema import succeeded.");
        } catch (SQLException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            auditLogService.record(
                    EventCategory.ADMIN_OPERATION, EventType.SCHEMA_IMPORTED, adminUserId, connectionId,
                    Result.FAILURE, connection.getName(),
                    "Schema import failed: " + e.getMessage());
            return new SchemaImportResult(false, 0, e.getMessage());
        }
    }

    private List<String> resolveSchemaNames(DatabaseMetaData metaData, String catalog, SchemaResolutionMode mode)
            throws SQLException {
        if (mode == SchemaResolutionMode.CATALOG_BASED) {
            return List.of(catalog);
        }
        List<String> schemaNames = new ArrayList<>();
        try (ResultSet schemas = metaData.getSchemas(catalog, null)) {
            while (schemas.next()) {
                schemaNames.add(schemas.getString("TABLE_SCHEM"));
            }
        }
        return schemaNames;
    }

    private SchemaTable upsertTable(
            Long connectionId, String schemaName, String tableName, TableType tableType, String comment, Instant now
    ) {
        return schemaTableRepository.findByConnectionIdAndSchemaNameAndTableName(connectionId, schemaName, tableName)
                .map(existing -> {
                    existing.update(tableType, comment, false, now);
                    return existing;
                })
                .orElseGet(() -> schemaTableRepository.save(
                        new SchemaTable(connectionId, schemaName, tableName, tableType, comment, false, now, now)));
    }

    private void importColumns(
            DatabaseMetaData metaData, String catalog, String schemaPattern, SchemaTable schemaTable,
            String tableName, TableType tableType, Instant now
    ) throws SQLException {
        Map<String, Integer> primaryKeySequences = new HashMap<>();
        if (tableType == TableType.TABLE) {
            try (ResultSet pks = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
                while (pks.next()) {
                    primaryKeySequences.put(pks.getString("COLUMN_NAME"), pks.getInt("KEY_SEQ"));
                }
            }
        }

        Set<Long> seenColumnIds = new HashSet<>();
        try (ResultSet columns = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME");
                boolean nullable = "YES".equals(columns.getString("IS_NULLABLE"));
                String comment = columns.getString("REMARKS");
                int ordinalPosition = columns.getInt("ORDINAL_POSITION");
                Integer primaryKeySequence = primaryKeySequences.get(columnName);

                SchemaColumn schemaColumn = upsertColumn(
                        schemaTable.getId(), columnName, dataType, nullable, comment,
                        ordinalPosition, primaryKeySequence, now);
                seenColumnIds.add(schemaColumn.getId());
            }
        }

        markStaleColumns(schemaTable.getId(), seenColumnIds, now);
    }

    private SchemaColumn upsertColumn(
            Long tableId, String columnName, String dataType, boolean nullable, String comment,
            int ordinalPosition, Integer primaryKeySequence, Instant now
    ) {
        return schemaColumnRepository.findByTableIdAndColumnName(tableId, columnName)
                .map(existing -> {
                    existing.update(dataType, nullable, comment, ordinalPosition, primaryKeySequence, false, now);
                    return existing;
                })
                .orElseGet(() -> schemaColumnRepository.save(
                        new SchemaColumn(tableId, columnName, dataType, nullable, comment,
                                ordinalPosition, primaryKeySequence, false, now, now)));
    }

    private void markStaleTables(Long connectionId, Set<Long> seenTableIds, Instant now) {
        for (SchemaTable existing : schemaTableRepository.findByConnectionId(connectionId)) {
            if (!seenTableIds.contains(existing.getId()) && !existing.isStale()) {
                existing.update(existing.getTableType(), existing.getComment(), true, now);
            }
        }
    }

    private void markStaleColumns(Long tableId, Set<Long> seenColumnIds, Instant now) {
        for (SchemaColumn existing : schemaColumnRepository.findByTableId(tableId)) {
            if (!seenColumnIds.contains(existing.getId()) && !existing.isStale()) {
                existing.update(existing.getDataType(), existing.isNullable(), existing.getComment(),
                        existing.getOrdinalPosition(), existing.getPrimaryKeySequence(), true, now);
            }
        }
    }

}