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

package cherry.mastermeister.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;

/**
 * P12（business-logic-model.md）を検証するプロパティテスト。
 */
@DataJpaTest
@JqwikSpringSupport
class SchemaQueryServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";

    private static final AtomicLong CONNECTION_ID_SEQ = new AtomicLong();

    @Autowired
    SchemaTableRepository tableRepository;
    @Autowired
    SchemaColumnRepository columnRepository;

    // SchemaQueryServiceには@Transactionalが付与されておらず、AOPプロキシを介する必要がないため
    // SchemaImportServiceTestのRollbackRoundTripとは異なりnewで直接生成すれば足りる。
    private SchemaQueryService newService() {
        return new SchemaQueryService(tableRepository, columnRepository);
    }

    // P12（前半）: listTablesが返す結果に、stale = trueのSchemaTableが含まれない。
    @Property(tries = 15)
    void listTablesExcludesStaleTables(@ForAll("staleFlagLists") List<Boolean> staleFlags) {
        Long connectionId = CONNECTION_ID_SEQ.incrementAndGet();
        Instant now = Instant.now();
        List<String> expectedNames = new ArrayList<>();
        for (int i = 0; i < staleFlags.size(); i++) {
            boolean stale = staleFlags.get(i);
            String tableName = "T" + i;
            tableRepository.save(new SchemaTable(
                    connectionId, TEST_SCHEMA, tableName, TableType.TABLE, null, stale, now, now));
            if (!stale) {
                expectedNames.add(tableName);
            }
        }

        List<String> actualNames = newService().listTables(connectionId, TEST_SCHEMA).stream()
                .map(TableMetadata::tableName)
                .toList();

        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames);
    }

    // P12（後半）: getTableDetailが返す結果に、stale = trueのSchemaColumnが含まれない。
    @Property(tries = 15)
    void getTableDetailExcludesStaleColumns(@ForAll("staleFlagLists") List<Boolean> staleFlags) {
        Long connectionId = CONNECTION_ID_SEQ.incrementAndGet();
        Instant now = Instant.now();
        SchemaTable table = tableRepository.save(new SchemaTable(
                connectionId, TEST_SCHEMA, "T1", TableType.TABLE, null, false, now, now));

        List<String> expectedNames = new ArrayList<>();
        for (int i = 0; i < staleFlags.size(); i++) {
            boolean stale = staleFlags.get(i);
            String columnName = "C" + i;
            columnRepository.save(new SchemaColumn(
                    table.getId(), columnName, "INT", true, null, i, null, stale, now, now));
            if (!stale) {
                expectedNames.add(columnName);
            }
        }

        List<String> actualNames = newService().getTableDetail(connectionId, TEST_SCHEMA, "T1").columns().stream()
                .map(ColumnDetail::columnName)
                .toList();

        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames);
    }

    @Provide
    Arbitrary<List<Boolean>> staleFlagLists() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(5);
    }

}