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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
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
import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;

/**
 * P7・P9（business-logic-model.md）を検証するプロパティテスト。
 */
class SqlParsingServiceTest {

    // P7: サブクエリ・UNION・CTE・ウィンドウ関数・OR条件・括弧グルーピングのいずれかを含む
    //     構文的に正しいSQLを入力した場合、常にfullyParsed = falseとなる。
    @Property(tries = 12)
    void parseRejectsUnsupportedSyntax(@ForAll("unsupportedSqlSamples") String sql) {
        SqlParsingService service = newService(RdbmsType.H2, permissiveResolver());
        try {
            ParseResult result = service.parse(1L, 1L, sql);
            assertThat(result.fullyParsed()).isFalse();
            assertThat(result.model()).isEmpty();
            assertThat(result.notice()).isPresent();
        } finally {
            service.shutdown();
        }
    }

    // P9: 解析対象SQLが呼び出しユーザにとってREAD未満のテーブル/カラムを1件でも参照する場合、
    //     構文的に完全に解析可能であっても常にfullyParsed = falseとなる。
    @Property(tries = 20)
    void parseRejectsWhenReferencedTableOrColumnLacksReadPermission(
            @ForAll boolean denyTable, @ForAll boolean denyColumn
    ) {
        Assume.that(denyTable || denyColumn);

        String sql = "SELECT a.col0 FROM s1.tbl a WHERE a.col1 = 'x'";

        EffectivePermissionResolver permissionResolver = mock(EffectivePermissionResolver.class);
        when(permissionResolver.listAccessibleSchemas(1L, 1L)).thenReturn(List.of("s1"));
        when(permissionResolver.resolveEffectiveTablePermission(1L, 1L, "s1", "tbl"))
                .thenReturn(denyTable ? Permission.NONE : Permission.READ);
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        columnPermissions.put("col0", denyColumn ? Permission.NONE : Permission.READ);
        columnPermissions.put("col1", Permission.READ);
        when(permissionResolver.resolveEffectiveColumnPermissions(1L, 1L, "s1", "tbl"))
                .thenReturn(columnPermissions);

        SqlParsingService service = newService(RdbmsType.H2, permissionResolver);
        try {
            ParseResult result = service.parse(1L, 1L, sql);
            assertThat(result.fullyParsed()).isFalse();
            assertThat(result.model()).isEmpty();
        } finally {
            service.shutdown();
        }
    }

    @Provide
    Arbitrary<String> unsupportedSqlSamples() {
        return Arbitraries.of(
                // サブクエリ（FROM句）
                "SELECT x.id FROM (SELECT id FROM tbl) x",
                // UNION
                "SELECT a.id FROM tbl a UNION SELECT b.id FROM tbl2 b",
                // CTE（WITH句）
                "WITH cte AS (SELECT id FROM tbl) SELECT c.id FROM cte c",
                // ウィンドウ関数
                "SELECT ROW_NUMBER() OVER (ORDER BY a.id) AS rn FROM tbl a",
                // OR条件
                "SELECT a.id FROM tbl a WHERE a.x = 1 OR a.y = 2",
                // 括弧グルーピング
                "SELECT a.id FROM tbl a WHERE (a.x = 1) AND (a.y = 2)"
        );
    }

    private EffectivePermissionResolver permissiveResolver() {
        EffectivePermissionResolver resolver = mock(EffectivePermissionResolver.class);
        when(resolver.listAccessibleSchemas(anyLong(), anyLong())).thenReturn(List.of("s1"));
        when(resolver.resolveEffectiveTablePermission(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Permission.READ);
        when(resolver.resolveEffectiveColumnPermissions(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Map.of());
        return resolver;
    }

    private SqlParsingService newService(RdbmsType rdbmsType, EffectivePermissionResolver permissionResolver) {
        RdbmsConnectionRepository connectionRepository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(new RdbmsConnection(
                "test", rdbmsType, "localhost", 1234, "db", "sa", "sa", null, now, now)));
        return new SqlParsingService(
                connectionRepository, new DialectStrategyFactory(allDialectStrategies()), permissionResolver,
                10000, Duration.ofSeconds(5), 2, 100, 10, 30, 30, 20, 20);
    }

    private List<DialectStrategy> allDialectStrategies() {
        return List.of(
                new H2DialectStrategy(), new MySqlDialectStrategy(),
                new MariaDbDialectStrategy(), new PostgreSqlDialectStrategy());
    }

}