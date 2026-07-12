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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.common.dialect.DialectStrategy;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.MariaDbDialectStrategy;
import cherry.mastermeister.common.dialect.MySqlDialectStrategy;
import cherry.mastermeister.common.dialect.PostgreSqlDialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.common.dialect.SortDirection;
import cherry.mastermeister.common.exception.ValidationException;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

/**
 * P2〜P6, P10（business-logic-model.md）を検証するプロパティテスト。
 */
class SqlGenerationServiceTest {

    private static final Pattern PARAM_PATTERN = Pattern.compile(":param\\d+");

    // P2: 生成SQLに含まれるJOINキーワードは常にINNER JOIN/LEFT JOIN/RIGHT JOINのいずれかであり、
    //     FULLは一切出現しない。
    @Property(tries = 20)
    void generateJoinKeywordIsAlwaysRestrictedSet(@ForAll("joinTypeLists") List<JoinType> joinTypes) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        List<JoinItem> joinItems = new ArrayList<>();
        for (int i = 0; i < joinTypes.size(); i++) {
            String alias = "j" + i;
            joinItems.add(new JoinItem(
                    joinTypes.get(i), "s1", "tbl" + i, alias,
                    new Condition("t0", "id", AggregateFunction.NONE, Operator.EQ, alias + ".id")));
        }
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "col0", AggregateFunction.NONE, null)),
                fromItem, joinItems, List.of(), List.of(), List.of(), List.of(), null, null);

        GeneratedSql result = newService(RdbmsType.H2).generate(1L, model);

        assertThat(result.sql()).doesNotContain("FULL");
        for (JoinType type : joinTypes) {
            String expectedKeyword = switch (type) {
                case INNER -> "INNER JOIN";
                case LEFT -> "LEFT JOIN";
                case RIGHT -> "RIGHT JOIN";
            };
            assertThat(result.sql()).contains(expectedKeyword);
        }
    }

    // P3: groupByColumnsが非空かつSELECT/ORDER BYにgroupByColumns未含有の非集計カラムが
    //     1件でも存在する場合、常にValidationExceptionとなる。
    @Property(tries = 20)
    void generateRejectsNonAggregatedColumnMissingFromGroupBy(@ForAll boolean violationInOrderBy) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        List<String> groupByColumns = List.of("t0.c0");
        List<SelectItem> selectItems;
        List<OrderByItem> orderByItems;
        if (violationInOrderBy) {
            selectItems = List.of(new SelectItem("t0", "c0", AggregateFunction.NONE, null));
            orderByItems = List.of(new OrderByItem("t0", "c_extra", AggregateFunction.NONE, SortDirection.ASC));
        } else {
            selectItems = List.of(new SelectItem("t0", "c_extra", AggregateFunction.NONE, null));
            orderByItems = List.of();
        }
        QueryBuilderModel model = new QueryBuilderModel(
                selectItems, fromItem, List.of(), List.of(), groupByColumns, List.of(), orderByItems, null, null);

        assertThatThrownBy(() -> newService(RdbmsType.H2).generate(1L, model))
                .isInstanceOf(ValidationException.class);
    }

    // P4: 生成SQLのWHERE/HAVING句にORキーワードや条件の括弧グルーピングが一切出現しない
    //     （whereConditions/havingConditionsは常にAND結合として組み立てられる）。
    @Property(tries = 20)
    void generateBuildsWhereAndHavingAsAndOnlyConjunction(
            @ForAll("conditionLists") List<Condition> conditions, @ForAll boolean targetHaving
    ) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "col0", AggregateFunction.NONE, null)),
                fromItem, List.of(),
                targetHaving ? List.of() : conditions,
                List.of(),
                targetHaving ? conditions : List.of(),
                List.of(), null, null);

        GeneratedSql result = newService(RdbmsType.H2).generate(1L, model);

        assertThat(result.sql()).doesNotContain(" OR ");
        assertThat(result.sql()).doesNotContain("(", ")");
        long andCount = result.sql().split(" AND ", -1).length - 1;
        assertThat(andCount).isEqualTo(Math.max(0, conditions.size() - 1));
    }

    // P5: GeneratedSql.sql中の:paramNプレースホルダ集合とGeneratedSql.params()のキー集合は
    //     常に一致する。
    @Property(tries = 20)
    void generateKeepsPlaceholdersAndParamsKeysInSync(
            @ForAll("conditionLists") List<Condition> whereConditions,
            @ForAll("conditionLists") List<Condition> havingConditions
    ) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "col0", AggregateFunction.NONE, null)),
                fromItem, List.of(), whereConditions, List.of(), havingConditions, List.of(), null, null);

        GeneratedSql result = newService(RdbmsType.H2).generate(1L, model);

        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PARAM_PATTERN.matcher(result.sql());
        while (matcher.find()) {
            placeholders.add(matcher.group().substring(1));
        }
        assertThat(placeholders).isEqualTo(result.params().keySet());
    }

    // P6: 生成SQLに含まれるテーブル名・カラム名・エイリアスは常にDialectStrategy.quoteIdentifierで
    //     クオートされた形で出現する。スキーマ名は方言によらず一切出現しない（環境間でのSQL
    //     再利用性を優先し、対象RDBMS接続の既定スキーマ解決に委ねる設計判断）。
    @Property(tries = 20)
    void generateAlwaysQuotesIdentifiers(@ForAll("rdbmsTypes") RdbmsType rdbmsType) {
        FromItem fromItem = new FromItem("myschema", "mytable", "t0");
        JoinItem joinItem = new JoinItem(
                JoinType.LEFT, "myschema", "jointable", "j0",
                new Condition("t0", "id", AggregateFunction.NONE, Operator.EQ, "j0.id"));
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "mycolumn", AggregateFunction.NONE, "myalias")),
                fromItem, List.of(joinItem), List.of(), List.of(), List.of(), List.of(), null, null);

        DialectStrategy dialect = new DialectStrategyFactory(allDialectStrategies()).resolve(rdbmsType);
        GeneratedSql result = newService(rdbmsType).generate(1L, model);

        assertThat(result.sql()).contains(dialect.quoteIdentifier("t0"));
        assertThat(result.sql()).contains(dialect.quoteIdentifier("mytable"));
        assertThat(result.sql()).contains(dialect.quoteIdentifier("mycolumn"));
        assertThat(result.sql()).contains(dialect.quoteIdentifier("myalias"));
        assertThat(result.sql()).contains(dialect.quoteIdentifier("j0"));
        assertThat(result.sql()).contains(dialect.quoteIdentifier("jointable"));
        assertThat(result.sql()).doesNotContain(dialect.quoteIdentifier("myschema"));
    }

    // P10: limit/offsetがnullの場合は常にLIMIT OFFSET句を含まないSQLが生成され、非nullの場合は
    //      常にDialectStrategy.buildPagingClauseが返す形式のLIMIT OFFSET句を含む。
    @Property(tries = 20)
    void generateLimitOffsetClausePresenceMatchesNullability(
            @ForAll boolean hasLimit, @ForAll("limitValues") int limit, @ForAll("offsetValues") Integer offset
    ) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        QueryBuilderModel model = new QueryBuilderModel(
                List.of(new SelectItem("t0", "col0", AggregateFunction.NONE, null)),
                fromItem, List.of(), List.of(), List.of(), List.of(), List.of(),
                hasLimit ? limit : null, hasLimit ? offset : null);

        DialectStrategy dialect = new H2DialectStrategy();
        GeneratedSql result = newService(RdbmsType.H2).generate(1L, model);

        if (hasLimit) {
            assertThat(result.sql()).contains(dialect.buildPagingClause(limit, offset == null ? 0 : offset));
        } else {
            assertThat(result.sql()).doesNotContain("LIMIT");
        }
    }

    @Provide
    Arbitrary<List<JoinType>> joinTypeLists() {
        return Arbitraries.of(JoinType.values()).list().ofMinSize(0).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<Condition>> conditionLists() {
        Arbitrary<Condition> conditionArb = Combinators.combine(
                Arbitraries.integers().between(0, 3), Arbitraries.of(Operator.values())
        ).as((colIndex, operator) -> new Condition(
                "t0", "c" + colIndex, AggregateFunction.NONE, operator,
                (operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) ? null : "v" + colIndex));
        return conditionArb.list().ofMinSize(1).ofMaxSize(4);
    }

    @Provide
    Arbitrary<RdbmsType> rdbmsTypes() {
        return Arbitraries.of(RdbmsType.values());
    }

    @Provide
    Arbitrary<Integer> limitValues() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<Integer> offsetValues() {
        return Arbitraries.integers().between(0, 100).injectNull(0.3);
    }

    private SqlGenerationService newService(RdbmsType rdbmsType) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", rdbmsType, "localhost", 1234, "db", "sa", "sa", null, now, now)));
        return new SqlGenerationService(
                connectionRepository, new DialectStrategyFactory(allDialectStrategies()),
                100, 10, 30, 30, 20, 20);
    }

    private List<DialectStrategy> allDialectStrategies() {
        return List.of(
                new H2DialectStrategy(), new MySqlDialectStrategy(),
                new MariaDbDialectStrategy(), new PostgreSqlDialectStrategy());
    }

}