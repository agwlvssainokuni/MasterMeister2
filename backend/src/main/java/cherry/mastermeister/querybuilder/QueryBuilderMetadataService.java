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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import cherry.mastermeister.permission.EffectivePermissionResolver;
import cherry.mastermeister.permission.Permission;
import cherry.mastermeister.rdbmsconnection.ConnectionSummary;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionRepository;
import cherry.mastermeister.schema.ColumnDetail;
import cherry.mastermeister.schema.SchemaQueryService;
import cherry.mastermeister.schema.TableMetadata;

@Service
public class QueryBuilderMetadataService {

    private final SchemaQueryService schemaQueryService;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;

    public QueryBuilderMetadataService(
            SchemaQueryService schemaQueryService,
            EffectivePermissionResolver effectivePermissionResolver,
            RdbmsConnectionRepository rdbmsConnectionRepository
    ) {
        this.schemaQueryService = schemaQueryService;
        this.effectivePermissionResolver = effectivePermissionResolver;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
    }

    public List<ConnectionSummary> listSelectableConnections(Long userId) {
        return rdbmsConnectionRepository.findAll().stream()
                .filter(c -> !effectivePermissionResolver.listAccessibleSchemas(userId, c.getId()).isEmpty())
                .map(c -> new ConnectionSummary(c.getId(), c.getName(), c.getRdbmsType(), c.getHost(), c.getDatabaseName()))
                .toList();
    }

    public List<String> listSelectableSchemas(Long userId, Long connectionId) {
        return effectivePermissionResolver.listAccessibleSchemas(userId, connectionId);
    }

    public List<TableRef> listSelectableTables(Long userId, Long connectionId, String schema) {
        List<String> tableNames = effectivePermissionResolver.listAccessibleTables(userId, connectionId, schema);
        Map<String, TableMetadata> metadataByName = schemaQueryService.listTables(connectionId, schema).stream()
                .collect(Collectors.toMap(TableMetadata::tableName, m -> m));
        return tableNames.stream()
                .map(tableName -> {
                    TableMetadata metadata = metadataByName.get(tableName);
                    return new TableRef(schema, tableName, metadata.comment());
                })
                .toList();
    }

    public List<ColumnRef> listSelectableColumns(Long userId, Long connectionId, String schema, String table) {
        Map<String, Permission> columnPermissions = effectivePermissionResolver
                .resolveEffectiveColumnPermissions(userId, connectionId, schema, table);
        List<ColumnDetail> columns = schemaQueryService.getTableDetail(connectionId, schema, table).columns();
        return columns.stream()
                .filter(column -> columnPermissions.getOrDefault(column.columnName(), Permission.NONE)
                        != Permission.NONE)
                .map(column -> new ColumnRef(column.columnName(), column.dataType(), column.nullable()))
                .toList();
    }

}