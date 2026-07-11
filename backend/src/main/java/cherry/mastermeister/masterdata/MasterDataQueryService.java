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

import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.NullsOrder;
import cherry.mastermeister.common.dialect.SchemaResolutionMode;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.PermissionDeniedException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.ConnectionSummary;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.schema.ColumnDetail;
import cherry.mastermeister.schema.SchemaQueryService;
import cherry.mastermeister.schema.TableMetadata;

@Service
public class MasterDataQueryService {

    private final SchemaQueryService schemaQueryService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final ConnectionPoolRegistry connectionPoolRegistry;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final AuditLogService auditLogService;
    private final Duration queryTimeout;
    private final int largeRecordThreshold;

    public MasterDataQueryService(
            SchemaQueryService schemaQueryService,
            EffectivePermissionResolver effectivePermissionResolver,
            ConnectionPoolRegistry connectionPoolRegistry,
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            AuditLogService auditLogService,
            @Value("${mm.app.master-data.query-timeout:30s}") Duration queryTimeout,
            @Value("${mm.app.master-data.large-record-threshold:100}") int largeRecordThreshold
    ) {
        this.schemaQueryService = schemaQueryService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.connectionPoolRegistry = connectionPoolRegistry;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.auditLogService = auditLogService;
        this.queryTimeout = queryTimeout;
        this.largeRecordThreshold = largeRecordThreshold;
    }

    public List<ConnectionSummary> listAccessibleConnections(Long userId) {
        return rdbmsConnectionRepository.findAll().stream()
                .filter(c -> !effectivePermissionResolver.listAccessibleSchemas(userId, c.getId()).isEmpty())
                .map(c -> new ConnectionSummary(c.getId(), c.getName(), c.getRdbmsType(), c.getHost(), c.getDatabaseName()))
                .toList();
    }

    public List<String> listAccessibleSchemas(Long userId, Long connectionId) {
        return effectivePermissionResolver.listAccessibleSchemas(userId, connectionId);
    }

    public List<TableSummary> listAccessibleTables(Long userId, Long connectionId, String schema) {
        List<String> tableNames = effectivePermissionResolver.listAccessibleTables(userId, connectionId, schema);
        Map<String, TableMetadata> metadataByName = schemaQueryService.listTables(connectionId, schema).stream()
                .collect(Collectors.toMap(TableMetadata::tableName, m -> m));

        List<TableSummary> summaries = new ArrayList<>();
        for (String tableName : tableNames) {
            TableMetadata metadata = metadataByName.get(tableName);
            Permission permission = effectivePermissionResolver
                    .resolveEffectiveTablePermission(userId, connectionId, schema, tableName);
            boolean canCreate = effectivePermissionResolver.canCreate(userId, connectionId, schema, tableName);
            boolean canDelete = effectivePermissionResolver.canDelete(userId, connectionId, schema, tableName);
            summaries.add(new TableSummary(
                    schema, tableName, metadata.tableType(), metadata.comment(), permission, canCreate, canDelete));
        }
        return summaries;
    }

    public RecordListResult listRecords(
            Long userId, Long connectionId, String schema, String table, FilterCriteria criteria, PageRequest page
    ) {
        Permission tablePermission = effectivePermissionResolver
                .resolveEffectiveTablePermission(userId, connectionId, schema, table);
        if (tablePermission == Permission.NONE) {
            throw new PermissionDeniedException("No access to table: " + schema + "." + table);
        }

        Map<String, Permission> columnPermissions = effectivePermissionResolver
                .resolveEffectiveColumnPermissions(userId, connectionId, schema, table);
        List<String> selectableColumns = columnPermissions.entrySet().stream()
                .filter(entry -> entry.getValue() != Permission.NONE)
                .map(Map.Entry::getKey)
                .toList();

        if (criteria.mode() == FilterMode.UI) {
            validateUiColumnPermissions(criteria, selectableColumns);
        } else {
            validateRawSqlSafety(criteria);
        }

        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));
        DialectStrategy dialect = dialectStrategyFactory.resolve(connection.getRdbmsType());
        String qualifiedTable = buildQualifiedTableName(dialect, schema, table);

        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(dialect, criteria, params);
        String orderByClause = buildOrderByClause(dialect, criteria);

        int pageSize = Math.max(page.pageSize(), 1);
        int pageNumber = Math.max(page.page(), 0);
        int offset = pageNumber * pageSize;

        String selectColumns = selectableColumns.stream()
                .map(dialect::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String countSql = "SELECT COUNT(*) FROM " + qualifiedTable + whereClause;
        String selectSql = "SELECT " + selectColumns + " FROM " + qualifiedTable + whereClause + orderByClause
                + " " + dialect.buildPagingClause(pageSize, offset);

        NamedParameterJdbcTemplate jdbcTemplate = connectionPoolRegistry.getJdbcTemplate(connectionId);
        ((JdbcTemplate) jdbcTemplate.getJdbcOperations()).setQueryTimeout((int) queryTimeout.toSeconds());

        long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);

        Map<String, Integer> primaryKeySequences = new HashMap<>();
        for (ColumnDetail column : schemaQueryService.getTableDetail(connectionId, schema, table).columns()) {
            primaryKeySequences.put(column.columnName(), column.primaryKeySequence());
        }

        RecordRowMapper rowMapper = new RecordRowMapper();
        QueryResult queryResult = jdbcTemplate.query(selectSql, params, rs -> {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<ColumnMetadata> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                columns.add(new ColumnMetadata(
                        columnName,
                        metaData.getColumnTypeName(i),
                        metaData.isNullable(i) != ResultSetMetaData.columnNoNulls,
                        primaryKeySequences.get(columnName),
                        columnPermissions.getOrDefault(columnName, Permission.NONE)));
            }
            List<List<Object>> rows = new ArrayList<>();
            int rowNum = 0;
            while (rs.next()) {
                rows.add(rowMapper.mapRow(rs, rowNum++));
            }
            return new QueryResult(columns, rows);
        });

        if (queryResult.rows().size() >= largeRecordThreshold) {
            auditLogService.record(
                    EventCategory.DATA_ACCESS, EventType.LARGE_RECORD_READ, userId, connectionId, Result.SUCCESS,
                    schema + "." + table, "Large record read: count=" + queryResult.rows().size());
        }

        PageResult<List<Object>> pageResult = new PageResult<>(queryResult.rows(), totalCount, pageNumber, pageSize);
        return new RecordListResult(queryResult.columns(), pageResult);
    }

    private void validateUiColumnPermissions(FilterCriteria criteria, List<String> selectableColumns) {
        for (UiCondition condition : criteria.uiConditions()) {
            if (!selectableColumns.contains(condition.columnName())) {
                throw new PermissionDeniedException("Column not readable: " + condition.columnName());
            }
        }
        for (UiSort sort : criteria.uiSorts()) {
            if (!selectableColumns.contains(sort.columnName())) {
                throw new PermissionDeniedException("Column not readable: " + sort.columnName());
            }
        }
    }

    private void validateRawSqlSafety(FilterCriteria criteria) {
        if (containsSemicolon(criteria.rawWhere()) || containsSemicolon(criteria.rawOrderBy())) {
            throw new PermissionDeniedException("Raw WHERE/ORDER BY must not contain multiple statements");
        }
    }

    private boolean containsSemicolon(String sql) {
        return sql != null && sql.contains(";");
    }

    private String buildQualifiedTableName(DialectStrategy dialect, String schema, String table) {
        if (dialect.getSchemaResolutionMode() == SchemaResolutionMode.CATALOG_BASED) {
            return dialect.quoteIdentifier(table);
        }
        return dialect.quoteIdentifier(schema) + "." + dialect.quoteIdentifier(table);
    }

    private String buildWhereClause(DialectStrategy dialect, FilterCriteria criteria, MapSqlParameterSource params) {
        if (criteria.mode() == FilterMode.RAW) {
            return (criteria.rawWhere() == null || criteria.rawWhere().isBlank())
                    ? "" : " WHERE " + criteria.rawWhere();
        }
        if (criteria.uiConditions().isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        int index = 0;
        for (UiCondition condition : criteria.uiConditions()) {
            String column = dialect.quoteIdentifier(condition.columnName());
            String paramName = "cond" + index++;
            clauses.add(switch (condition.operator()) {
                case EQ -> column + " = :" + paramName;
                case NE -> column + " <> :" + paramName;
                case GT -> column + " > :" + paramName;
                case LT -> column + " < :" + paramName;
                case GE -> column + " >= :" + paramName;
                case LE -> column + " <= :" + paramName;
                case LIKE -> column + " LIKE :" + paramName;
                case IS_NULL -> column + " IS NULL";
                case IS_NOT_NULL -> column + " IS NOT NULL";
            });
            if (condition.operator() != Operator.IS_NULL && condition.operator() != Operator.IS_NOT_NULL) {
                params.addValue(paramName, condition.value());
            }
        }
        return " WHERE " + String.join(" AND ", clauses);
    }

    private String buildOrderByClause(DialectStrategy dialect, FilterCriteria criteria) {
        if (criteria.mode() == FilterMode.RAW) {
            return (criteria.rawOrderBy() == null || criteria.rawOrderBy().isBlank())
                    ? "" : " ORDER BY " + criteria.rawOrderBy();
        }
        if (criteria.uiSorts().isEmpty()) {
            return "";
        }
        String clauses = criteria.uiSorts().stream()
                .map(sort -> dialect.quoteIdentifier(sort.columnName()) + " "
                        + dialect.buildNullsOrderingClause(sort.direction(), NullsOrder.LAST))
                .collect(Collectors.joining(", "));
        return " ORDER BY " + clauses;
    }

    private record QueryResult(List<ColumnMetadata> columns, List<List<Object>> rows) {
    }

}