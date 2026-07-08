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

package cherry.mastermeister.common.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * P10〜P12（business-logic-model.md、P9再識別）を検証するプロパティテスト。
 * {@code dialectStrategies()} が4実装すべてを供給することで、各性質を全方言に対して検証する。
 */
class DialectStrategyTest {

    // P10: quoteIdentifierは構文的に妥当な識別子クォートを返す
    @Property
    void quoteIdentifierIsSyntacticallyValid(
            @ForAll("dialectStrategies") DialectStrategy strategy,
            @ForAll("identifiers") String identifier
    ) {
        String quoted = strategy.quoteIdentifier(identifier);
        String quoteChar = String.valueOf(quoteCharFor(strategy));

        assertThat(quoted).startsWith(quoteChar);
        assertThat(quoted).endsWith(quoteChar);

        String inner = quoted.substring(1, quoted.length() - 1);
        assertThat(inner.replace(quoteChar + quoteChar, ""))
                .doesNotContain(quoteChar);
    }

    // P11: buildPagingClauseはlimit/offsetの非負整数に対し常に構文的に妥当なSQL断片となる
    @Property
    void buildPagingClauseIsSyntacticallyValidForNonNegativeArguments(
            @ForAll("dialectStrategies") DialectStrategy strategy,
            @ForAll("nonNegativeInts") int limit,
            @ForAll("nonNegativeInts") int offset
    ) {
        String clause = strategy.buildPagingClause(limit, offset);

        assertThat(clause).contains("LIMIT " + limit);
        assertThat(clause).contains("OFFSET " + offset);
    }

    // P12: buildNullsOrderingClauseはNullsOrderの指定と並び順意図が矛盾しない句を生成する
    @Property
    void buildNullsOrderingClauseIsConsistentWithDirectionAndNullsOrder(
            @ForAll("dialectStrategies") DialectStrategy strategy,
            @ForAll("sortDirections") SortDirection direction,
            @ForAll("nullsOrders") NullsOrder nullsOrder
    ) {
        String clause = strategy.buildNullsOrderingClause(direction, nullsOrder);

        boolean supportsNativeNullsOrdering = strategy.getRdbmsType() == RdbmsType.POSTGRESQL
                || strategy.getRdbmsType() == RdbmsType.H2;
        String expected = supportsNativeNullsOrdering
                ? direction.name() + " NULLS " + nullsOrder.name()
                : direction.name();
        assertThat(clause).isEqualTo(expected);
    }

    private static char quoteCharFor(DialectStrategy strategy) {
        return switch (strategy.getRdbmsType()) {
            case MYSQL, MARIADB -> '`';
            case POSTGRESQL, H2 -> '"';
        };
    }

    @Provide
    Arbitrary<DialectStrategy> dialectStrategies() {
        return Arbitraries.of(
                new MySqlDialectStrategy(), new MariaDbDialectStrategy(),
                new PostgreSqlDialectStrategy(), new H2DialectStrategy());
    }

    @Provide
    Arbitrary<String> identifiers() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('`', '"', '_')
                .ofMinLength(1)
                .ofMaxLength(15);
    }

    @Provide
    Arbitrary<Integer> nonNegativeInts() {
        return Arbitraries.integers().between(0, 10_000);
    }

    @Provide
    Arbitrary<SortDirection> sortDirections() {
        return Arbitraries.of(SortDirection.class);
    }

    @Provide
    Arbitrary<NullsOrder> nullsOrders() {
        return Arbitraries.of(NullsOrder.class);
    }

}