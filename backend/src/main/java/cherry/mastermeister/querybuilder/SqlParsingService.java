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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.SchemaResolutionMode;
import cherry.mastermeister.common.dialect.SortDirection;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

@Service
public class SqlParsingService {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final ExecutorService executor;
    private final int parseMaxLength;
    private final Duration parseTimeout;
    private final int maxSelectItems;
    private final int maxJoinItems;
    private final int maxWhereConditions;
    private final int maxGroupByColumns;
    private final int maxHavingConditions;
    private final int maxOrderByItems;

    public SqlParsingService(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            EffectivePermissionResolver effectivePermissionResolver,
            @Value("${mm.app.query-builder.parse-max-length:10000}") int parseMaxLength,
            @Value("${mm.app.query-builder.parse-timeout:5s}") Duration parseTimeout,
            @Value("${mm.app.query-builder.parse-executor-pool-size:4}") int parseExecutorPoolSize,
            @Value("${mm.app.query-builder.max-select-items:100}") int maxSelectItems,
            @Value("${mm.app.query-builder.max-join-items:10}") int maxJoinItems,
            @Value("${mm.app.query-builder.max-where-conditions:30}") int maxWhereConditions,
            @Value("${mm.app.query-builder.max-group-by-columns:30}") int maxGroupByColumns,
            @Value("${mm.app.query-builder.max-having-conditions:20}") int maxHavingConditions,
            @Value("${mm.app.query-builder.max-order-by-items:20}") int maxOrderByItems
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.parseMaxLength = parseMaxLength;
        this.parseTimeout = parseTimeout;
        this.executor = Executors.newFixedThreadPool(parseExecutorPoolSize);
        this.maxSelectItems = maxSelectItems;
        this.maxJoinItems = maxJoinItems;
        this.maxWhereConditions = maxWhereConditions;
        this.maxGroupByColumns = maxGroupByColumns;
        this.maxHavingConditions = maxHavingConditions;
        this.maxOrderByItems = maxOrderByItems;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public ParseResult parse(Long userId, Long connectionId, String rawSql) {
        if (rawSql == null || rawSql.length() > parseMaxLength) {
            return notFullyParsed("入力SQLが長すぎるため解析できません");
        }

        Statement statement;
        Future<Statement> future = executor.submit(() -> CCJSqlParserUtil.parse(rawSql));
        try {
            statement = future.get(parseTimeout.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return notFullyParsed("解析に時間がかかりすぎたため中断しました");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return notFullyParsed("SQLを解析できませんでした");
        } catch (Exception e) {
            return notFullyParsed("SQLを解析できませんでした");
        }

        if (!(statement instanceof Select select)) {
            return notFullyParsed("SELECT文のみ解析できます");
        }
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            return notFullyParsed("対応していない構文です（WITH句）");
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            return notFullyParsed("対応していない構文です（UNION/サブクエリ等）");
        }

        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));
        DialectStrategy dialect = dialectStrategyFactory.resolve(connection.getRdbmsType());
        boolean catalogBased = dialect.getSchemaResolutionMode() == SchemaResolutionMode.CATALOG_BASED;
        List<String> accessibleSchemas = effectivePermissionResolver.listAccessibleSchemas(userId, connectionId);

        if (!(plainSelect.getFromItem() instanceof Table fromTable)) {
            return notFullyParsed("対応していない構文です（FROM句にサブクエリ）");
        }
        Optional<FromItem> fromItem = resolveFromItem(fromTable, catalogBased, accessibleSchemas);
        if (fromItem.isEmpty()) {
            return notFullyParsed("対応していない構文です（FROM句の解決に失敗）");
        }

        List<JoinItem> joinItems = new ArrayList<>();
        for (Join join : Optional.ofNullable(plainSelect.getJoins()).orElseGet(List::of)) {
            Optional<JoinItem> joinItem = resolveJoinItem(join, catalogBased, accessibleSchemas);
            if (joinItem.isEmpty()) {
                return notFullyParsed("対応していない構文です（JOIN句）");
            }
            joinItems.add(joinItem.get());
        }
        if (joinItems.size() > maxJoinItems) {
            return notFullyParsed("JOIN句の件数が上限を超えています");
        }

        List<SelectItem> selectItems = new ArrayList<>();
        for (net.sf.jsqlparser.statement.select.SelectItem<?> item : plainSelect.getSelectItems()) {
            AggregateExpressionVisitor visitor = AggregateExpressionVisitor.parse(item.getExpression());
            if (!visitor.isSupported()) {
                return notFullyParsed("対応していない構文です（SELECT句）");
            }
            String outputAlias = item.getAlias() != null ? item.getAlias().getName() : null;
            selectItems.add(new SelectItem(
                    visitor.tableAlias(), visitor.columnName(), visitor.aggregateFunction(), outputAlias));
        }
        if (selectItems.size() > maxSelectItems) {
            return notFullyParsed("SELECT句の件数が上限を超えています");
        }

        List<Condition> whereConditions = List.of();
        if (plainSelect.getWhere() != null) {
            Optional<List<Condition>> parsed = WhereConditionVisitor.parse(plainSelect.getWhere());
            if (parsed.isEmpty()) {
                return notFullyParsed("対応していない構文です（WHERE句）");
            }
            whereConditions = parsed.get();
        }
        if (whereConditions.size() > maxWhereConditions) {
            return notFullyParsed("WHERE句の件数が上限を超えています");
        }

        List<String> groupByColumns = new ArrayList<>();
        GroupByElement groupByElement = plainSelect.getGroupBy();
        if (groupByElement != null) {
            for (Object exprObj : groupByElement.getGroupByExpressionList()) {
                if (!(exprObj instanceof Column column) || column.getTable() == null
                        || column.getTable().getName() == null) {
                    return notFullyParsed("対応していない構文です（GROUP BY句）");
                }
                groupByColumns.add(column.getTable().getName() + "." + column.getColumnName());
            }
        }
        if (groupByColumns.size() > maxGroupByColumns) {
            return notFullyParsed("GROUP BY句の件数が上限を超えています");
        }

        List<Condition> havingConditions = List.of();
        if (plainSelect.getHaving() != null) {
            Optional<List<Condition>> parsed = WhereConditionVisitor.parse(plainSelect.getHaving());
            if (parsed.isEmpty()) {
                return notFullyParsed("対応していない構文です（HAVING句）");
            }
            havingConditions = parsed.get();
        }
        if (havingConditions.size() > maxHavingConditions) {
            return notFullyParsed("HAVING句の件数が上限を超えています");
        }

        List<OrderByItem> orderByItems = new ArrayList<>();
        for (OrderByElement element : Optional.ofNullable(plainSelect.getOrderByElements()).orElseGet(List::of)) {
            AggregateExpressionVisitor visitor = AggregateExpressionVisitor.parse(element.getExpression());
            if (!visitor.isSupported()) {
                return notFullyParsed("対応していない構文です（ORDER BY句）");
            }
            SortDirection direction = element.isAscDescPresent() && !element.isAsc()
                    ? SortDirection.DESC : SortDirection.ASC;
            orderByItems.add(new OrderByItem(
                    visitor.tableAlias(), visitor.columnName(), visitor.aggregateFunction(), direction));
        }
        if (orderByItems.size() > maxOrderByItems) {
            return notFullyParsed("ORDER BY句の件数が上限を超えています");
        }

        Limit limitClause = plainSelect.getLimit();
        Integer limit = null;
        if (limitClause != null) {
            if (!(limitClause.getRowCount() instanceof LongValue lv)) {
                return notFullyParsed("対応していない構文です（LIMIT句）");
            }
            limit = (int) lv.getValue();
        }
        Offset offsetClause = plainSelect.getOffset();
        Integer offset = null;
        if (offsetClause != null) {
            if (!(offsetClause.getOffset() instanceof LongValue lv)) {
                return notFullyParsed("対応していない構文です（OFFSET句）");
            }
            offset = (int) lv.getValue();
        }

        QueryBuilderModel model = new QueryBuilderModel(
                selectItems, fromItem.get(), joinItems, whereConditions, groupByColumns, havingConditions,
                orderByItems, limit, offset);

        if (!hasReadPermission(userId, connectionId, model)) {
            return notFullyParsed("アクセス権限のないカラム/テーブルを含むため反映できません");
        }

        return new ParseResult(true, Optional.of(model), Optional.empty());
    }

    private ParseResult notFullyParsed(String notice) {
        return new ParseResult(false, Optional.empty(), Optional.of(notice));
    }

    private Optional<FromItem> resolveFromItem(Table table, boolean catalogBased, List<String> accessibleSchemas) {
        if (table.getAlias() == null || table.getAlias().getName() == null) {
            return Optional.empty();
        }
        return resolveSchema(table.getSchemaName(), catalogBased, accessibleSchemas)
                .map(schema -> new FromItem(schema, table.getName(), table.getAlias().getName()));
    }

    private Optional<JoinItem> resolveJoinItem(Join join, boolean catalogBased, List<String> accessibleSchemas) {
        JoinType type;
        if (join.isInner()) {
            type = JoinType.INNER;
        } else if (join.isLeft()) {
            type = JoinType.LEFT;
        } else if (join.isRight()) {
            type = JoinType.RIGHT;
        } else {
            return Optional.empty();
        }
        if (!(join.getRightItem() instanceof Table table)
                || table.getAlias() == null || table.getAlias().getName() == null) {
            return Optional.empty();
        }
        Optional<String> schema = resolveSchema(table.getSchemaName(), catalogBased, accessibleSchemas);
        if (schema.isEmpty()) {
            return Optional.empty();
        }
        Collection<Expression> onExpressions = join.getOnExpressions();
        if (onExpressions == null || onExpressions.size() != 1) {
            return Optional.empty();
        }
        Optional<Condition> onCondition = parseOnCondition(onExpressions.iterator().next());
        if (onCondition.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JoinItem(
                type, schema.get(), table.getName(), table.getAlias().getName(), onCondition.get()));
    }

    private Optional<Condition> parseOnCondition(Expression onExpr) {
        Operator operator;
        Expression leftExpr;
        Expression rightExpr;
        if (onExpr instanceof EqualsTo e) {
            operator = Operator.EQ;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else if (onExpr instanceof NotEqualsTo e) {
            operator = Operator.NE;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else if (onExpr instanceof GreaterThan e) {
            operator = Operator.GT;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else if (onExpr instanceof GreaterThanEquals e) {
            operator = Operator.GE;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else if (onExpr instanceof MinorThan e) {
            operator = Operator.LT;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else if (onExpr instanceof MinorThanEquals e) {
            operator = Operator.LE;
            leftExpr = e.getLeftExpression();
            rightExpr = e.getRightExpression();
        } else {
            return Optional.empty();
        }

        AggregateExpressionVisitor left = AggregateExpressionVisitor.parse(leftExpr);
        if (!left.isSupported()) {
            return Optional.empty();
        }
        if (!(rightExpr instanceof Column rightColumn) || rightColumn.getTable() == null
                || rightColumn.getTable().getName() == null) {
            return Optional.empty();
        }
        String value = rightColumn.getTable().getName() + "." + rightColumn.getColumnName();
        return Optional.of(
                new Condition(left.tableAlias(), left.columnName(), left.aggregateFunction(), operator, value));
    }

    private Optional<String> resolveSchema(String explicitSchema, boolean catalogBased, List<String> accessibleSchemas) {
        if (explicitSchema != null) {
            return Optional.of(explicitSchema);
        }
        if (catalogBased && accessibleSchemas.size() == 1) {
            return Optional.of(accessibleSchemas.get(0));
        }
        return Optional.empty();
    }

    private boolean hasReadPermission(Long userId, Long connectionId, QueryBuilderModel model) {
        Map<String, String[]> aliasToTable = new HashMap<>();
        aliasToTable.put(model.fromItem().alias(), new String[]{model.fromItem().schema(), model.fromItem().table()});
        for (JoinItem joinItem : model.joinItems()) {
            aliasToTable.put(joinItem.alias(), new String[]{joinItem.schema(), joinItem.table()});
        }

        for (String[] schemaTable : aliasToTable.values()) {
            if (effectivePermissionResolver.resolveEffectiveTablePermission(
                    userId, connectionId, schemaTable[0], schemaTable[1]) == Permission.NONE) {
                return false;
            }
        }

        Set<String> referencedAliasColumns = new LinkedHashSet<>();
        model.selectItems().forEach(item -> referencedAliasColumns.add(item.tableAlias() + "." + item.columnName()));
        model.whereConditions().forEach(
                condition -> referencedAliasColumns.add(condition.tableAlias() + "." + condition.columnName()));
        referencedAliasColumns.addAll(model.groupByColumns());
        model.havingConditions().forEach(
                condition -> referencedAliasColumns.add(condition.tableAlias() + "." + condition.columnName()));
        model.orderByItems().forEach(item -> referencedAliasColumns.add(item.tableAlias() + "." + item.columnName()));
        model.joinItems().forEach(joinItem -> {
            Condition on = joinItem.onCondition();
            referencedAliasColumns.add(on.tableAlias() + "." + on.columnName());
            referencedAliasColumns.add(String.valueOf(on.value()));
        });

        for (String aliasColumn : referencedAliasColumns) {
            int dot = aliasColumn.indexOf('.');
            if (dot < 0) {
                return false;
            }
            String alias = aliasColumn.substring(0, dot);
            String column = aliasColumn.substring(dot + 1);
            String[] schemaTable = aliasToTable.get(alias);
            if (schemaTable == null) {
                return false;
            }
            Map<String, Permission> columnPermissions = effectivePermissionResolver
                    .resolveEffectiveColumnPermissions(userId, connectionId, schemaTable[0], schemaTable[1]);
            if (columnPermissions.getOrDefault(column, Permission.NONE) == Permission.NONE) {
                return false;
            }
        }
        return true;
    }

}