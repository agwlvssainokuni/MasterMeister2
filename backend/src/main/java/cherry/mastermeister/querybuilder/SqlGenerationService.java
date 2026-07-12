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

package cherry.mastermeister.querybuilder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.NullsOrder;
import cherry.mastermeister.common.dialect.SchemaResolutionMode;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

@Service
public class SqlGenerationService {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final int maxSelectItems;
    private final int maxJoinItems;
    private final int maxWhereConditions;
    private final int maxGroupByColumns;
    private final int maxHavingConditions;
    private final int maxOrderByItems;

    public SqlGenerationService(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            @Value("${mm.app.query-builder.max-select-items:100}") int maxSelectItems,
            @Value("${mm.app.query-builder.max-join-items:10}") int maxJoinItems,
            @Value("${mm.app.query-builder.max-where-conditions:30}") int maxWhereConditions,
            @Value("${mm.app.query-builder.max-group-by-columns:30}") int maxGroupByColumns,
            @Value("${mm.app.query-builder.max-having-conditions:20}") int maxHavingConditions,
            @Value("${mm.app.query-builder.max-order-by-items:20}") int maxOrderByItems
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.maxSelectItems = maxSelectItems;
        this.maxJoinItems = maxJoinItems;
        this.maxWhereConditions = maxWhereConditions;
        this.maxGroupByColumns = maxGroupByColumns;
        this.maxHavingConditions = maxHavingConditions;
        this.maxOrderByItems = maxOrderByItems;
    }

    public GeneratedSql generate(Long connectionId, QueryBuilderModel model) {
        validateCounts(model);
        validateGroupByConstraint(model);

        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));
        DialectStrategy dialect = dialectStrategyFactory.resolve(connection.getRdbmsType());

        Map<String, Object> params = new LinkedHashMap<>();
        int[] paramCounter = {1};

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(buildSelectClause(dialect, model));
        sql.append(buildFromJoinClause(dialect, model));
        if (!model.whereConditions().isEmpty()) {
            sql.append(" WHERE ").append(buildConditionClause(dialect, model.whereConditions(), params, paramCounter));
        }
        if (!model.groupByColumns().isEmpty()) {
            sql.append(" GROUP BY ").append(buildGroupByClause(dialect, model.groupByColumns()));
        }
        if (!model.havingConditions().isEmpty()) {
            sql.append(" HAVING ")
                    .append(buildConditionClause(dialect, model.havingConditions(), params, paramCounter));
        }
        if (!model.orderByItems().isEmpty()) {
            sql.append(" ORDER BY ").append(buildOrderByClause(dialect, model.orderByItems()));
        }
        if (model.limit() != null) {
            sql.append(" ").append(dialect.buildPagingClause(model.limit(), model.offset() == null ? 0 : model.offset()));
        }

        return new GeneratedSql(sql.toString(), params);
    }

    private void validateCounts(QueryBuilderModel model) {
        if (model.selectItems().size() > maxSelectItems) {
            throw new ValidationException("Too many select items: " + model.selectItems().size());
        }
        if (model.joinItems().size() > maxJoinItems) {
            throw new ValidationException("Too many join items: " + model.joinItems().size());
        }
        if (model.whereConditions().size() > maxWhereConditions) {
            throw new ValidationException("Too many where conditions: " + model.whereConditions().size());
        }
        if (model.groupByColumns().size() > maxGroupByColumns) {
            throw new ValidationException("Too many group by columns: " + model.groupByColumns().size());
        }
        if (model.havingConditions().size() > maxHavingConditions) {
            throw new ValidationException("Too many having conditions: " + model.havingConditions().size());
        }
        if (model.orderByItems().size() > maxOrderByItems) {
            throw new ValidationException("Too many order by items: " + model.orderByItems().size());
        }
    }

    private void validateGroupByConstraint(QueryBuilderModel model) {
        if (model.groupByColumns().isEmpty()) {
            return;
        }
        Set<String> groupByKeys = new LinkedHashSet<>(model.groupByColumns());
        for (SelectItem item : model.selectItems()) {
            if (item.aggregateFunction() == AggregateFunction.NONE
                    && !groupByKeys.contains(item.tableAlias() + "." + item.columnName())) {
                throw new ValidationException(
                        "Non-aggregated select column not in GROUP BY: "
                                + item.tableAlias() + "." + item.columnName());
            }
        }
        for (OrderByItem item : model.orderByItems()) {
            if (item.aggregateFunction() == AggregateFunction.NONE
                    && !groupByKeys.contains(item.tableAlias() + "." + item.columnName())) {
                throw new ValidationException(
                        "Non-aggregated order by column not in GROUP BY: "
                                + item.tableAlias() + "." + item.columnName());
            }
        }
    }

    private String buildSelectClause(DialectStrategy dialect, QueryBuilderModel model) {
        return model.selectItems().stream()
                .map(item -> {
                    String expr = renderAggregateExpr(
                            dialect, item.tableAlias(), item.columnName(), item.aggregateFunction());
                    if (item.outputAlias() != null && !item.outputAlias().isBlank()) {
                        expr += " AS " + dialect.quoteIdentifier(item.outputAlias());
                    }
                    return expr;
                })
                .collect(Collectors.joining(", "));
    }

    private String buildFromJoinClause(DialectStrategy dialect, QueryBuilderModel model) {
        StringBuilder clause = new StringBuilder();
        FromItem fromItem = model.fromItem();
        clause.append(" FROM ").append(qualifiedTableName(dialect, fromItem.schema(), fromItem.table()))
                .append(" AS ").append(dialect.quoteIdentifier(fromItem.alias()));
        for (JoinItem joinItem : model.joinItems()) {
            clause.append(" ").append(joinKeyword(joinItem.type())).append(" ")
                    .append(qualifiedTableName(dialect, joinItem.schema(), joinItem.table()))
                    .append(" AS ").append(dialect.quoteIdentifier(joinItem.alias()))
                    .append(" ON ").append(renderOnCondition(dialect, joinItem.onCondition()));
        }
        return clause.toString();
    }

    private String joinKeyword(JoinType type) {
        return switch (type) {
            case INNER -> "INNER JOIN";
            case LEFT -> "LEFT JOIN";
            case RIGHT -> "RIGHT JOIN";
        };
    }

    private String renderOnCondition(DialectStrategy dialect, Condition onCondition) {
        String left = renderAggregateExpr(
                dialect, onCondition.tableAlias(), onCondition.columnName(), onCondition.aggregateFunction());
        String rightColumnRef = String.valueOf(onCondition.value());
        int dot = rightColumnRef.indexOf('.');
        String right = dot < 0
                ? dialect.quoteIdentifier(rightColumnRef)
                : renderColumnRef(dialect, rightColumnRef.substring(0, dot), rightColumnRef.substring(dot + 1));
        return left + " " + operatorSql(onCondition.operator()) + " " + right;
    }

    private String buildConditionClause(
            DialectStrategy dialect, List<Condition> conditions, Map<String, Object> params, int[] paramCounter
    ) {
        return conditions.stream()
                .map(condition -> {
                    String left = renderAggregateExpr(
                            dialect, condition.tableAlias(), condition.columnName(), condition.aggregateFunction());
                    if (condition.operator() == Operator.IS_NULL) {
                        return left + " IS NULL";
                    }
                    if (condition.operator() == Operator.IS_NOT_NULL) {
                        return left + " IS NOT NULL";
                    }
                    String paramName = "param" + paramCounter[0]++;
                    params.put(paramName, condition.value());
                    return left + " " + operatorSql(condition.operator()) + " :" + paramName;
                })
                .collect(Collectors.joining(" AND "));
    }

    private String operatorSql(Operator operator) {
        return switch (operator) {
            case EQ -> "=";
            case NE -> "<>";
            case GT -> ">";
            case LT -> "<";
            case GE -> ">=";
            case LE -> "<=";
            case LIKE -> "LIKE";
            case IS_NULL, IS_NOT_NULL -> throw new IllegalStateException("Unexpected operator: " + operator);
        };
    }

    private String buildGroupByClause(DialectStrategy dialect, List<String> groupByColumns) {
        return groupByColumns.stream()
                .map(column -> {
                    int dot = column.indexOf('.');
                    return dot < 0
                            ? dialect.quoteIdentifier(column)
                            : renderColumnRef(dialect, column.substring(0, dot), column.substring(dot + 1));
                })
                .collect(Collectors.joining(", "));
    }

    private String buildOrderByClause(DialectStrategy dialect, List<OrderByItem> orderByItems) {
        return orderByItems.stream()
                .map(item -> renderAggregateExpr(dialect, item.tableAlias(), item.columnName(), item.aggregateFunction())
                        + " " + dialect.buildNullsOrderingClause(item.direction(), NullsOrder.LAST))
                .collect(Collectors.joining(", "));
    }

    private String renderAggregateExpr(
            DialectStrategy dialect, String tableAlias, String columnName, AggregateFunction aggregateFunction
    ) {
        String columnRef = renderColumnRef(dialect, tableAlias, columnName);
        return aggregateFunction == AggregateFunction.NONE
                ? columnRef
                : aggregateFunction.name() + "(" + columnRef + ")";
    }

    private String renderColumnRef(DialectStrategy dialect, String tableAlias, String columnName) {
        return dialect.quoteIdentifier(tableAlias) + "." + dialect.quoteIdentifier(columnName);
    }

    private String qualifiedTableName(DialectStrategy dialect, String schema, String table) {
        if (dialect.getSchemaResolutionMode() == SchemaResolutionMode.CATALOG_BASED) {
            return dialect.quoteIdentifier(table);
        }
        return dialect.quoteIdentifier(schema) + "." + dialect.quoteIdentifier(table);
    }

}