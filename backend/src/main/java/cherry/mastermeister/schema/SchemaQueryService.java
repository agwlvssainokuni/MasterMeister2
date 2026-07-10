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

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import cherry.mastermeister.common.exception.EntityNotFoundException;

@Service
public class SchemaQueryService {

    private final SchemaTableRepository schemaTableRepository;
    private final SchemaColumnRepository schemaColumnRepository;

    public SchemaQueryService(
            SchemaTableRepository schemaTableRepository,
            SchemaColumnRepository schemaColumnRepository
    ) {
        this.schemaTableRepository = schemaTableRepository;
        this.schemaColumnRepository = schemaColumnRepository;
    }

    public List<String> listSchemas(Long connectionId) {
        return schemaTableRepository.findByConnectionIdAndStaleFalse(connectionId).stream()
                .map(SchemaTable::getSchemaName)
                .distinct()
                .toList();
    }

    public List<TableMetadata> listTables(Long connectionId, String schema) {
        return schemaTableRepository.findByConnectionIdAndSchemaNameAndStaleFalse(connectionId, schema).stream()
                .map(table -> new TableMetadata(
                        table.getSchemaName(), table.getTableName(), table.getTableType(), table.getComment()))
                .toList();
    }

    public TableDetail getTableDetail(Long connectionId, String schema, String table) {
        SchemaTable schemaTable = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse(connectionId, schema, table)
                .orElseThrow(() -> new EntityNotFoundException(
                        "SchemaTable not found: connectionId=" + connectionId + ", schema=" + schema
                                + ", table=" + table));

        List<ColumnDetail> columns = schemaColumnRepository.findByTableIdAndStaleFalse(schemaTable.getId()).stream()
                .sorted(Comparator.comparing(
                        SchemaColumn::getPrimaryKeySequence, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(column -> new ColumnDetail(
                        column.getColumnName(), column.getDataType(), column.isNullable(), column.getComment(),
                        column.getOrdinalPosition(), column.getPrimaryKeySequence()))
                .toList();

        return new TableDetail(
                schemaTable.getSchemaName(), schemaTable.getTableName(), schemaTable.getTableType(),
                schemaTable.getComment(), columns);
    }

}