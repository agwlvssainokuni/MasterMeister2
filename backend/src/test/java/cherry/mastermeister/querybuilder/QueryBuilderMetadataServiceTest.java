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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.schema.ColumnDetail;
import cherry.mastermeister.schema.SchemaQueryService;
import cherry.mastermeister.schema.TableDetail;
import cherry.mastermeister.schema.TableType;

/**
 * P1（business-logic-model.md）を検証するプロパティテスト。
 */
class QueryBuilderMetadataServiceTest {

    private static final String TEST_SCHEMA = "TESTSCHEMA";
    private static final List<String> COLUMNS = List.of("COL0", "COL1", "COL2", "COL3");

    // P1: listSelectableColumnsが返すColumnRefは常に実効カラム権限がREAD以上のカラムのみであり、
    //     READ未満（NONE）のカラムは結果に一切含まれない。
    @Property(tries = 20)
    void listSelectableColumnsExcludesBelowReadPermissionColumns(
            @ForAll("columnPermissionPatterns") List<Permission> permissions
    ) {
        Map<String, Permission> columnPermissions = new LinkedHashMap<>();
        List<ColumnDetail> columns = new ArrayList<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            columns.add(new ColumnDetail(COLUMNS.get(i), "VARCHAR", true, null, i + 1, null));
            columnPermissions.put(COLUMNS.get(i), permissions.get(i));
        }
        List<String> expectedSelectable = columnPermissions.entrySet().stream()
                .filter(entry -> entry.getValue() != Permission.NONE)
                .map(Map.Entry::getKey)
                .toList();

        SchemaQueryService schemaQueryService = mock(SchemaQueryService.class);
        when(schemaQueryService.getTableDetail(1L, TEST_SCHEMA, "T1"))
                .thenReturn(new TableDetail(TEST_SCHEMA, "T1", TableType.TABLE, null, columns));
        EffectivePermissionResolver permissionResolver = mock(EffectivePermissionResolver.class);
        when(permissionResolver.resolveEffectiveColumnPermissions(1L, 1L, TEST_SCHEMA, "T1"))
                .thenReturn(columnPermissions);

        QueryBuilderMetadataService service =
                new QueryBuilderMetadataService(schemaQueryService, permissionResolver);
        List<ColumnRef> result = service.listSelectableColumns(1L, 1L, TEST_SCHEMA, "T1");

        assertThat(result).extracting(ColumnRef::columnName).containsExactlyElementsOf(expectedSelectable);
        assertThat(result).allMatch(
                column -> columnPermissions.get(column.columnName()) != Permission.NONE);
    }

    @Provide
    Arbitrary<List<Permission>> columnPermissionPatterns() {
        return Arbitraries.of(Permission.values()).list().ofSize(COLUMNS.size());
    }

}