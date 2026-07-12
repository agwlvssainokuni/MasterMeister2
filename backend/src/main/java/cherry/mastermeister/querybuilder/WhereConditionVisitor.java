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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;

/**
 * WHERE/HAVING共通のAND結合のみの条件式木を{@link Condition}のフラットな{@link List}へ変換する。
 * 未対応の式（{@code OR}・括弧グルーピング・サブクエリ・{@code IN}/{@code BETWEEN}等）に遭遇した
 * 場合は、対応する{@code visit}メソッドをオーバーライドしていないため既定の{@code null}が返り、
 * それが呼び出し元へ伝播することで自動的に「解析不能」として扱われる。
 */
public class WhereConditionVisitor extends ExpressionVisitorAdapter<Boolean> {

    private final List<Condition> conditions = new ArrayList<>();

    public static Optional<List<Condition>> parse(Expression expression) {
        WhereConditionVisitor visitor = new WhereConditionVisitor();
        Boolean result = expression.accept(visitor, null);
        return Boolean.TRUE.equals(result) ? Optional.of(visitor.conditions) : Optional.empty();
    }

    @Override
    public <S> Boolean visit(AndExpression andExpression, S context) {
        Boolean left = andExpression.getLeftExpression().accept(this, context);
        Boolean right = andExpression.getRightExpression().accept(this, context);
        return Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right);
    }

    @Override
    public <S> Boolean visit(EqualsTo equalsTo, S context) {
        return addComparison(equalsTo.getLeftExpression(), equalsTo.getRightExpression(), Operator.EQ);
    }

    @Override
    public <S> Boolean visit(NotEqualsTo notEqualsTo, S context) {
        return addComparison(notEqualsTo.getLeftExpression(), notEqualsTo.getRightExpression(), Operator.NE);
    }

    @Override
    public <S> Boolean visit(GreaterThan greaterThan, S context) {
        return addComparison(greaterThan.getLeftExpression(), greaterThan.getRightExpression(), Operator.GT);
    }

    @Override
    public <S> Boolean visit(GreaterThanEquals greaterThanEquals, S context) {
        return addComparison(
                greaterThanEquals.getLeftExpression(), greaterThanEquals.getRightExpression(), Operator.GE);
    }

    @Override
    public <S> Boolean visit(MinorThan minorThan, S context) {
        return addComparison(minorThan.getLeftExpression(), minorThan.getRightExpression(), Operator.LT);
    }

    @Override
    public <S> Boolean visit(MinorThanEquals minorThanEquals, S context) {
        return addComparison(minorThanEquals.getLeftExpression(), minorThanEquals.getRightExpression(), Operator.LE);
    }

    @Override
    public <S> Boolean visit(LikeExpression likeExpression, S context) {
        if (likeExpression.isNot()) {
            return false;
        }
        return addComparison(likeExpression.getLeftExpression(), likeExpression.getRightExpression(), Operator.LIKE);
    }

    @Override
    public <S> Boolean visit(IsNullExpression isNullExpression, S context) {
        AggregateExpressionVisitor left = AggregateExpressionVisitor.parse(isNullExpression.getLeftExpression());
        if (!left.isSupported()) {
            return false;
        }
        conditions.add(new Condition(
                left.tableAlias(), left.columnName(), left.aggregateFunction(),
                isNullExpression.isNot() ? Operator.IS_NOT_NULL : Operator.IS_NULL, null));
        return true;
    }

    private Boolean addComparison(Expression leftExpr, Expression rightExpr, Operator operator) {
        AggregateExpressionVisitor left = AggregateExpressionVisitor.parse(leftExpr);
        if (!left.isSupported()) {
            return false;
        }
        Optional<Object> value = literalValue(rightExpr);
        if (value.isEmpty()) {
            return false;
        }
        conditions.add(new Condition(
                left.tableAlias(), left.columnName(), left.aggregateFunction(), operator, value.get()));
        return true;
    }

    private Optional<Object> literalValue(Expression expression) {
        if (expression instanceof LongValue v) {
            return Optional.of(v.getValue());
        }
        if (expression instanceof DoubleValue v) {
            return Optional.of(v.getValue());
        }
        if (expression instanceof StringValue v) {
            return Optional.of(v.getValue());
        }
        if (expression instanceof BooleanValue v) {
            return Optional.of(v.getValue());
        }
        if (expression instanceof DateValue v) {
            return Optional.of(v.getValue().toLocalDate());
        }
        if (expression instanceof TimeValue v) {
            return Optional.of(v.getValue().toLocalTime());
        }
        if (expression instanceof TimestampValue v) {
            return Optional.of(v.getValue().toLocalDateTime());
        }
        return Optional.empty();
    }

}