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

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;

/**
 * 単一カラム、または単一引数の集計関数（{@code COUNT/SUM/AVG/MIN/MAX}）式のみを解釈する。
 * それ以外の式形状（算術式・CASE式・サブクエリ・{@code COUNT(*)}等）は{@link #isSupported()}を
 * {@code false}のままにする。
 */
public class AggregateExpressionVisitor extends ExpressionVisitorAdapter<Void> {

    private boolean supported = false;
    private String tableAlias;
    private String columnName;
    private AggregateFunction aggregateFunction;

    public static AggregateExpressionVisitor parse(Expression expression) {
        AggregateExpressionVisitor visitor = new AggregateExpressionVisitor();
        expression.accept(visitor);
        return visitor;
    }

    public boolean isSupported() {
        return supported;
    }

    public String tableAlias() {
        return tableAlias;
    }

    public String columnName() {
        return columnName;
    }

    public AggregateFunction aggregateFunction() {
        return aggregateFunction;
    }

    @Override
    public <S> Void visit(Column column, S context) {
        if (column.getTable() == null || column.getTable().getName() == null) {
            return null;
        }
        this.tableAlias = column.getTable().getName();
        this.columnName = column.getColumnName();
        this.aggregateFunction = AggregateFunction.NONE;
        this.supported = true;
        return null;
    }

    @Override
    public <S> Void visit(Function function, S context) {
        if (function.isAllColumns()) {
            return null;
        }
        AggregateFunction fn;
        try {
            fn = AggregateFunction.valueOf(function.getName().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (fn == AggregateFunction.NONE) {
            return null;
        }
        ExpressionList<?> parameters = function.getParameters();
        if (parameters == null || parameters.size() != 1) {
            return null;
        }
        Expression singleParam = parameters.get(0);
        if (!(singleParam instanceof Column column)) {
            return null;
        }
        if (column.getTable() == null || column.getTable().getName() == null) {
            return null;
        }
        this.tableAlias = column.getTable().getName();
        this.columnName = column.getColumnName();
        this.aggregateFunction = fn;
        this.supported = true;
        return null;
    }

}