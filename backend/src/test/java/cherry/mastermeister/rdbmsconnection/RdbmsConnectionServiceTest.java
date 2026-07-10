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

package cherry.mastermeister.rdbmsconnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.MariaDbDialectStrategy;
import cherry.mastermeister.common.dialect.MySqlDialectStrategy;
import cherry.mastermeister.common.dialect.PostgreSqlDialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;

/**
 * P3・P6（business-logic-model.md）を検証するプロパティテスト。
 */
class RdbmsConnectionServiceTest {

    private final DialectStrategyFactory dialectStrategyFactory = new DialectStrategyFactory(
            List.of(new MySqlDialectStrategy(), new MariaDbDialectStrategy(),
                    new PostgreSqlDialectStrategy(), new H2DialectStrategy()));

    private final RdbmsConnectionService service = new RdbmsConnectionService(
            mock(RdbmsConnectionRepository.class), dialectStrategyFactory,
            mock(ConnectionPoolRegistry.class), mock(AuditLogService.class), Duration.ofSeconds(5));

    // P3: additionalParamsがnull/空/空白のみの場合、組み立てたJDBC URLはbuildJdbcUrlが返す
    // 構造化ベースURLと完全に一致する（末尾付加なし）。
    @Property
    void jdbcUrlEqualsBaseUrlWhenAdditionalParamsAbsent(@ForAll("configsWithoutAdditionalParams") ConnectionConfig config) {
        String baseUrl = dialectStrategyFactory.resolve(config.rdbmsType())
                .buildJdbcUrl(config.host(), config.port(), config.databaseName());

        assertThat(service.buildJdbcUrl(config)).isEqualTo(baseUrl);
    }

    // P3: additionalParamsが非空の場合、組み立てたJDBC URLは構造化ベースURLで始まり、
    // "?" + additionalParams が末尾に1回だけ付加される。
    @Property
    void jdbcUrlAppendsAdditionalParamsOnceWhenPresent(@ForAll("configsWithAdditionalParams") ConnectionConfig config) {
        String baseUrl = dialectStrategyFactory.resolve(config.rdbmsType())
                .buildJdbcUrl(config.host(), config.port(), config.databaseName());

        String jdbcUrl = service.buildJdbcUrl(config);

        assertThat(jdbcUrl).isEqualTo(baseUrl + "?" + config.additionalParams());
        assertThat(jdbcUrl.indexOf("?")).isEqualTo(jdbcUrl.lastIndexOf("?"));
    }

    // P6: 未保存の設定でtestConnectionを呼び出しても、ConnectionPoolRegistryのキャッシュ状態
    // （登録済みプールの集合）は呼び出し前後で変化しない。
    @Property(tries = 10)
    void registryCacheUnchangedForUnsavedConfig(@ForAll("configsForCacheCheck") ConnectionConfig config) {
        ConnectionPoolRegistry registry = mock(ConnectionPoolRegistry.class);
        RdbmsConnectionService testService = new RdbmsConnectionService(
                mock(RdbmsConnectionRepository.class), dialectStrategyFactory,
                registry, mock(AuditLogService.class), Duration.ofMillis(200));

        try {
            testService.testConnection(config);
        } catch (RuntimeException ignored) {
            // 接続失敗時にHikariCPが送出する可能性のある例外はP6の関心事ではない。
        }

        verifyNoInteractions(registry);
    }

    // P6: 既存接続IDの再テストでtestConnectionを呼び出しても、ConnectionPoolRegistryの
    // キャッシュ状態は呼び出し前後で変化しない。
    @Property(tries = 10)
    void registryCacheUnchangedForExistingConnectionId(
            @ForAll("connectionIds") Long connectionId, @ForAll("configsForCacheCheck") ConnectionConfig config) {
        ConnectionPoolRegistry registry = mock(ConnectionPoolRegistry.class);
        RdbmsConnectionRepository repository = mock(RdbmsConnectionRepository.class);
        Instant now = Instant.now();
        when(repository.findById(connectionId)).thenReturn(Optional.of(new RdbmsConnection(
                config.name(), config.rdbmsType(), config.host(), config.port(),
                config.databaseName(), config.username(), config.password(),
                config.additionalParams(), now, now)));
        RdbmsConnectionService testService = new RdbmsConnectionService(
                repository, dialectStrategyFactory, registry, mock(AuditLogService.class), Duration.ofMillis(200));

        try {
            testService.testConnection(connectionId);
        } catch (RuntimeException ignored) {
            // 接続失敗時にHikariCPが送出する可能性のある例外はP6の関心事ではない。
        }

        verifyNoInteractions(registry);
    }

    @Provide
    Arbitrary<Long> connectionIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    @Provide
    Arbitrary<ConnectionConfig> configsForCacheCheck() {
        Arbitrary<String> name = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<RdbmsType> rdbmsType = Arbitraries.of(RdbmsType.class);
        Arbitrary<String> host = Arbitraries.just("localhost");
        Arbitrary<Integer> port = Arbitraries.integers().between(1, 65535);
        Arbitrary<String> databaseName = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> username = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> password = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> additionalParams = Arbitraries.just((String) null);
        return Combinators.combine(name, rdbmsType, host, port, databaseName, username, password, additionalParams)
                .as(ConnectionConfig::new);
    }

    @Provide
    Arbitrary<ConnectionConfig> configsWithoutAdditionalParams() {
        Arbitrary<String> additionalParams = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.of("", " ", "  \t"));
        return connectionConfigs(additionalParams);
    }

    @Provide
    Arbitrary<ConnectionConfig> configsWithAdditionalParams() {
        Arbitrary<String> additionalParams = Arbitraries.strings().alpha().numeric()
                .withChars('=', '&').ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.isBlank());
        return connectionConfigs(additionalParams);
    }

    private Arbitrary<ConnectionConfig> connectionConfigs(Arbitrary<String> additionalParams) {
        Arbitrary<String> name = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<RdbmsType> rdbmsType = Arbitraries.of(RdbmsType.class);
        Arbitrary<String> host = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<Integer> port = Arbitraries.integers().between(1, 65535);
        Arbitrary<String> databaseName = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> username = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> password = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        return Combinators.combine(name, rdbmsType, host, port, databaseName, username, password, additionalParams)
                .as(ConnectionConfig::new);
    }

}