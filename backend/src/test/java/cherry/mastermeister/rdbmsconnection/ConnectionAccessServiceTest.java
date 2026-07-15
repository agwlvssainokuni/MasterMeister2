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

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cherry.mastermeister.common.dialect.RdbmsType;
import cherry.mastermeister.permission.EffectivePermissionResolver;

/**
 * P13（U3 business-logic-model.mdフロー6）を検証するプロパティテスト。
 */
@DataJpaTest
@JqwikSpringSupport
class ConnectionAccessServiceTest {

    private static final long USER_ID = 1L;

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    // P13: 返される接続一覧は、必ずlistAccessibleSchemasが空でない結果を返す接続のみで構成される。
    @Property(tries = 20)
    void listAccessibleConnectionsReturnsOnlyConnectionsWithAccessibleSchemas(
            @ForAll("accessibilityFlags") List<Boolean> accessibleFlags
    ) {
        rdbmsConnectionRepository.deleteAll();
        Instant now = Instant.now();
        List<RdbmsConnection> saved = accessibleFlags.stream()
                .map(ignored -> rdbmsConnectionRepository.save(new RdbmsConnection(
                        "conn", RdbmsType.POSTGRESQL, "localhost", 5432, "db", "user",
                        "secret", null, now, now)))
                .toList();

        EffectivePermissionResolver resolver = mock(EffectivePermissionResolver.class);
        Set<Long> expectedAccessibleIds = new LinkedHashSet<>();
        for (int i = 0; i < saved.size(); i++) {
            Long connectionId = saved.get(i).getId();
            boolean accessible = accessibleFlags.get(i);
            when(resolver.listAccessibleSchemas(USER_ID, connectionId))
                    .thenReturn(accessible ? List.of("public") : List.of());
            if (accessible) {
                expectedAccessibleIds.add(connectionId);
            }
        }

        ConnectionAccessService service = new ConnectionAccessService(rdbmsConnectionRepository, resolver);
        List<ConnectionSummary> result = service.listAccessibleConnections(USER_ID);

        assertThat(result).extracting(ConnectionSummary::id).containsExactlyInAnyOrderElementsOf(expectedAccessibleIds);
    }

    @Provide
    Arbitrary<List<Boolean>> accessibilityFlags() {
        return Arbitraries.of(true, false).list().ofMinSize(0).ofMaxSize(6);
    }

}