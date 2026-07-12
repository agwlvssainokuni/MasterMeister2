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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

/**
 * P8（business-logic-model.md）を検証するプロパティテスト。{@code SqlGenerationService}と
 * {@code SqlParsingService}の両方を利用するため、単一責務の{@code SqlParsingServiceTest}とは
 * 独立したクラスとした（Step 3-4実装時の判断）。{@code generate}が返す{@code GeneratedSql}は
 * {@code :paramN}プレースホルダを含み、{@code parse}は手入力の（リテラル値を含む）SQLを
 * 対象とするため、往復検証にあたっては{@code params}の値をSQLリテラルとして埋め戻した文字列を
 * {@code parse}に渡す（本番コードの仕様ではなく、テスト側の往復検証のための変換）。
 */
class QueryBuilderRoundTripTest {

    // P8: generateが受理する任意のQueryBuilderModelについて、generate(model)の結果（パラメータを
    //     リテラル値へ埋め戻した上）をparseしたParseResultはfullyParsed = trueであり、その
    //     modelは元のmodelと値の集合として等価である。
    @Property(tries = 20)
    void generateThenParseRoundTripsToEquivalentModel(
            @ForAll boolean includeJoin,
            @ForAll("selectColumnLists") List<String> selectColumns,
            @ForAll("whereConditionLists") List<Condition> whereConditions,
            @ForAll("orderByItemLists") List<OrderByItem> orderByItems,
            @ForAll boolean hasLimit,
            @ForAll("limitValues") int limit,
            @ForAll("limitValues") int offset
    ) {
        FromItem fromItem = new FromItem("s1", "base", "t0");
        List<JoinItem> joinItems = includeJoin
                ? List.of(new JoinItem(
                        JoinType.LEFT, "s1", "joined", "j0",
                        new Condition("t0", "id", AggregateFunction.NONE, Operator.EQ, "j0.jid")))
                : List.of();
        List<SelectItem> selectItems = selectColumns.stream()
                .map(column -> new SelectItem("t0", column, AggregateFunction.NONE, null))
                .toList();

        QueryBuilderModel model = new QueryBuilderModel(
                selectItems, fromItem, joinItems, whereConditions, List.of(), List.of(), orderByItems,
                hasLimit ? limit : null, hasLimit ? offset : null);

        SqlGenerationService generationService = newGenerationService();
        SqlParsingService parsingService = newParsingService(permissiveResolver());
        try {
            GeneratedSql generated = generationService.generate(1L, model);
            String materializedSql = materialize(generated);

            ParseResult result = parsingService.parse(1L, 1L, materializedSql);

            assertThat(result.fullyParsed()).isTrue();
            assertThat(result.notice()).isEmpty();
            assertThat(result.model()).contains(model);
        } finally {
            parsingService.shutdown();
        }
    }

    @Provide
    Arbitrary<List<String>> selectColumnLists() {
        return Arbitraries.of("c0", "c1").list().ofMinSize(1).ofMaxSize(2);
    }

    @Provide
    Arbitrary<List<Condition>> whereConditionLists() {
        Arbitrary<Condition> conditionArb = Combinators.combine(
                Arbitraries.of("c2", "c3"), Arbitraries.of(Operator.values())
        ).as(this::buildCondition);
        return conditionArb.list().ofMinSize(0).ofMaxSize(2);
    }

    @Provide
    Arbitrary<List<OrderByItem>> orderByItemLists() {
        Arbitrary<OrderByItem> itemArb = Combinators.combine(
                Arbitraries.of("c0", "c1"), Arbitraries.of(SortDirection.values())
        ).as((column, direction) -> new OrderByItem("t0", column, AggregateFunction.NONE, direction));
        return itemArb.list().ofMinSize(0).ofMaxSize(2);
    }

    @Provide
    Arbitrary<Integer> limitValues() {
        return Arbitraries.integers().between(0, 50);
    }

    private Condition buildCondition(String column, Operator operator) {
        Object value = switch (operator) {
            case LIKE -> "abc%";
            case IS_NULL, IS_NOT_NULL -> null;
            default -> 42L;
        };
        return new Condition("t0", column, AggregateFunction.NONE, operator, value);
    }

    private String materialize(GeneratedSql generated) {
        String sql = generated.sql();
        List<String> keys = new ArrayList<>(generated.params().keySet());
        keys.sort(Comparator.comparingInt((String key) -> Integer.parseInt(key.substring(5))).reversed());
        for (String key : keys) {
            sql = sql.replace(":" + key, toSqlLiteral(generated.params().get(key)));
        }
        return sql;
    }

    private String toSqlLiteral(Object value) {
        if (value instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        }
        if (value instanceof Long || value instanceof Integer) {
            return value.toString();
        }
        throw new IllegalStateException("Unsupported literal type in round-trip test: " + value);
    }

    private EffectivePermissionResolver permissiveResolver() {
        EffectivePermissionResolver resolver = mock(EffectivePermissionResolver.class);
        when(resolver.listAccessibleSchemas(anyLong(), anyLong())).thenReturn(List.of("s1"));
        when(resolver.resolveEffectiveTablePermission(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Permission.READ);
        Map<String, Permission> allReadColumns = new LinkedHashMap<>();
        for (String column : List.of("c0", "c1", "c2", "c3", "id", "jid")) {
            allReadColumns.put(column, Permission.READ);
        }
        when(resolver.resolveEffectiveColumnPermissions(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(allReadColumns);
        return resolver;
    }

    private SqlGenerationService newGenerationService() {
        return new SqlGenerationService(
                newConnectionRepository(), new DialectStrategyFactory(allDialectStrategies()),
                100, 10, 30, 30, 20, 20);
    }

    private SqlParsingService newParsingService(EffectivePermissionResolver permissionResolver) {
        return new SqlParsingService(
                newConnectionRepository(), new DialectStrategyFactory(allDialectStrategies()), permissionResolver,
                10000, Duration.ofSeconds(5), 2, 100, 10, 30, 30, 20, 20);
    }

    private RdbmsConnectionRepository newConnectionRepository() {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", 1234, "db", "sa", "sa", null, now, now)));
        return connectionRepository;
    }

    private List<DialectStrategy> allDialectStrategies() {
        return List.of(
                new H2DialectStrategy(), new MySqlDialectStrategy(),
                new MariaDbDialectStrategy(), new PostgreSqlDialectStrategy());
    }

}