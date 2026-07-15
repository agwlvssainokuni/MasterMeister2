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

package cherry.mastermeister.queryexecution;

import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

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
import cherry.mastermeister.queryhistory.ExecutionRecord;
import cherry.mastermeister.queryhistory.QueryHistoryService;
import cherry.mastermeister.rdbmsconnection.ConnectionPoolRegistry;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.savedquery.SavedQueryDetail;
import cherry.mastermeister.savedquery.SavedQueryService;

@Service
public class QueryExecutionService {

    private final SavedQueryService savedQueryService;
    private final ReadOnlySqlValidator readOnlySqlValidator;
    private final SqlParamDetector sqlParamDetector;
    private final PagingSqlBuilder pagingSqlBuilder;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final ConnectionPoolRegistry connectionPoolRegistry;
    private final QueryHistoryService queryHistoryService;
    private final AuditLogService auditLogService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final Duration queryTimeout;
    private final int maxResultRows;

    public QueryExecutionService(
            SavedQueryService savedQueryService,
            ReadOnlySqlValidator readOnlySqlValidator,
            SqlParamDetector sqlParamDetector,
            PagingSqlBuilder pagingSqlBuilder,
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            ConnectionPoolRegistry connectionPoolRegistry,
            QueryHistoryService queryHistoryService,
            AuditLogService auditLogService,
            EffectivePermissionResolver effectivePermissionResolver,
            @Value("${mm.app.query-execution.query-timeout:30s}") Duration queryTimeout,
            @Value("${mm.app.query-execution.max-result-rows:1000}") int maxResultRows
    ) {
        this.savedQueryService = savedQueryService;
        this.readOnlySqlValidator = readOnlySqlValidator;
        this.sqlParamDetector = sqlParamDetector;
        this.pagingSqlBuilder = pagingSqlBuilder;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.connectionPoolRegistry = connectionPoolRegistry;
        this.queryHistoryService = queryHistoryService;
        this.auditLogService = auditLogService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.queryTimeout = queryTimeout;
        this.maxResultRows = maxResultRows;
    }

    public QueryResult executeAdhocSql(
            Long userId, Long connectionId, String schema, String sql, Map<String, Object> params,
            PagingOption paging
    ) {
        return execute(userId, connectionId, schema, sql, params, paging, null, null);
    }

    public QueryResult executeSavedQuery(
            Long userId, Long connectionId, String schema, Long savedQueryId, Map<String, Object> params,
            PagingOption paging
    ) {
        SavedQueryDetail savedQuery = savedQueryService.getExecutableQuery(userId, savedQueryId);
        if (!savedQuery.connectionId().equals(connectionId)) {
            throw new ValidationException("connectionId does not match the saved query: " + savedQueryId);
        }
        return execute(
                userId, connectionId, schema, savedQuery.sql(), params, paging, savedQueryId, savedQuery.name());
    }

    public List<String> listAccessibleSchemas(Long userId, Long connectionId) {
        return effectivePermissionResolver.listAccessibleSchemas(userId, connectionId);
    }

    private QueryResult execute(
            Long userId, Long connectionId, String schema, String sql, Map<String, Object> params,
            PagingOption paging, Long savedQueryId, String savedQueryName
    ) {
        readOnlySqlValidator.validate(sql);
        validateParams(sql, params);

        if (!effectivePermissionResolver.listAccessibleSchemas(userId, connectionId).contains(schema)) {
            throw new PermissionDeniedException("Schema not accessible: " + schema);
        }

        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));
        DialectStrategy dialect = dialectStrategyFactory.resolve(connection.getRdbmsType());

        Instant executedAt = Instant.now();
        long start = System.currentTimeMillis();
        QueryResult result = runQuery(sql, params, paging, dialect, connectionId, schema);
        long elapsedMillis = System.currentTimeMillis() - start;

        Integer executionCount = savedQueryId != null
                ? savedQueryService.incrementExecutionCount(savedQueryId)
                : null;

        queryHistoryService.recordExecution(new ExecutionRecord(
                userId, connectionId, schema, sql, params, result.rows().size(), elapsedMillis, executedAt,
                savedQueryId, savedQueryName, executionCount));
        auditLogService.record(
                EventCategory.DATA_ACCESS, EventType.QUERY_EXECUTED, userId, connectionId, Result.SUCCESS,
                savedQueryId != null ? "savedQueryId=" + savedQueryId : "adhoc",
                "Query executed: rows=" + result.rows().size());

        return result;
    }

    private void validateParams(String sql, Map<String, Object> params) {
        for (DetectedParam detected : sqlParamDetector.detect(sql)) {
            if (!params.containsKey(detected.name())) {
                throw new ValidationException("Missing parameter: " + detected.name());
            }
        }
    }

    private QueryResult runQuery(
            String sql, Map<String, Object> params, PagingOption paging, DialectStrategy dialect, Long connectionId,
            String schema
    ) {
        String executableSql = sql;
        if (paging.enabled()) {
            int pageSize = Math.max(paging.pageSize(), 1);
            int offset = Math.max(paging.page(), 0) * pageSize;
            executableSql = pagingSqlBuilder.wrapWithPaging(sql, dialect, pageSize, offset);
        }

        MapSqlParameterSource paramSource = new MapSqlParameterSource(params);
        int rowLimit = paging.enabled() ? Integer.MAX_VALUE : maxResultRows;
        String finalSql = executableSql;

        JdbcTemplate connectionBorrowingTemplate = new JdbcTemplate(connectionPoolRegistry.getDataSource(connectionId));
        return connectionBorrowingTemplate.execute((ConnectionCallback<QueryResult>) connection -> {
            if (dialect.getSchemaResolutionMode() == SchemaResolutionMode.SCHEMA_BASED) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(dialect.buildSetSchemaStatement(dialect.quoteIdentifier(schema)));
                }
            }

            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
            ((JdbcTemplate) jdbcTemplate.getJdbcOperations()).setQueryTimeout((int) queryTimeout.toSeconds());

            return jdbcTemplate.query(finalSql, paramSource, rs -> {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<ResultColumn> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(new ResultColumn(metaData.getColumnLabel(i), metaData.getColumnTypeName(i)));
                }
                List<List<Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (rows.size() >= rowLimit) {
                        truncated = true;
                        break;
                    }
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
                return new QueryResult(columns, rows, rows.size(), truncated);
            });
        });
    }

}