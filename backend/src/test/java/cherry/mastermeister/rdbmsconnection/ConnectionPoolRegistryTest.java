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
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.h2.tools.Server;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.dialect.H2DialectStrategy;
import cherry.mastermeister.common.dialect.RdbmsType;

/**
 * P4・P5（business-logic-model.md）を検証するプロパティテスト。
 */
class ConnectionPoolRegistryTest {

    private static Server h2Server;

    private final DialectStrategyFactory dialectStrategyFactory =
            new DialectStrategyFactory(List.of(new H2DialectStrategy()));

    @BeforeContainer
    static void startH2Server() throws SQLException {
        h2Server = Server.createTcpServer("-tcpPort", "0", "-tcpAllowOthers", "-ifNotExists").start();
    }

    @AfterContainer
    static void stopH2Server() {
        if (h2Server != null) {
            h2Server.stop();
        }
    }

    // P4: getDataSource(id)を同一idで複数回呼び出すと常に同一インスタンスを返す（Idempotence）。
    @Property(tries = 20)
    void getDataSourceIsIdempotent(@ForAll("connectionIds") Long connectionId) {
        ConnectionPoolRegistry registry = newRegistry(connectionId);
        try {
            DataSource first = registry.getDataSource(connectionId);
            DataSource second = registry.getDataSource(connectionId);

            assertThat(second).isSameAs(first);
        } finally {
            registry.invalidate(connectionId);
        }
    }

    // P5: invalidate(id)後にgetDataSource(id)を呼び出すと、invalidate前とは異なる新規インスタンスを
    // 返す（キャッシュが再生成されるInvariant）。
    @Property(tries = 20)
    void getDataSourceReturnsNewInstanceAfterInvalidate(@ForAll("connectionIds") Long connectionId) {
        ConnectionPoolRegistry registry = newRegistry(connectionId);
        try {
            DataSource first = registry.getDataSource(connectionId);
            registry.invalidate(connectionId);
            DataSource second = registry.getDataSource(connectionId);

            assertThat(second).isNotSameAs(first);
        } finally {
            registry.invalidate(connectionId);
        }
    }

    @Provide
    Arbitrary<Long> connectionIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    private ConnectionPoolRegistry newRegistry(Long connectionId) {
        RdbmsConnectionRepository repository = mock(RdbmsConnectionRepository.class);
        when(repository.findById(connectionId)).thenReturn(Optional.of(newH2Connection()));
        return new ConnectionPoolRegistry(repository, dialectStrategyFactory, 1, 0, Duration.ofSeconds(5));
    }

    private RdbmsConnection newH2Connection() {
        Instant now = Instant.now();
        return new RdbmsConnection(
                "test", RdbmsType.H2, "localhost", h2Server.getPort(),
                "mem:pooltest;DB_CLOSE_DELAY=-1", "sa", "sa", null, now, now);
    }

}