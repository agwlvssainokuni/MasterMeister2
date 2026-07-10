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

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rdbms-connections/{connectionId}")
public class SchemaController {

    private final SchemaImportService schemaImportService;
    private final SchemaQueryService schemaQueryService;

    public SchemaController(SchemaImportService schemaImportService, SchemaQueryService schemaQueryService) {
        this.schemaImportService = schemaImportService;
        this.schemaQueryService = schemaQueryService;
    }

    @PostMapping("/schema-import")
    public SchemaImportResult importSchema(@PathVariable Long connectionId, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        return schemaImportService.importSchema(connectionId, adminUserId);
    }

    @GetMapping("/schemas")
    public List<String> listSchemas(@PathVariable Long connectionId) {
        return schemaQueryService.listSchemas(connectionId);
    }

    @GetMapping("/schemas/{schema}/tables")
    public List<TableMetadata> listTables(@PathVariable Long connectionId, @PathVariable String schema) {
        return schemaQueryService.listTables(connectionId, schema);
    }

    @GetMapping("/schemas/{schema}/tables/{table}")
    public TableDetail getTableDetail(
            @PathVariable Long connectionId, @PathVariable String schema, @PathVariable String table) {
        return schemaQueryService.getTableDetail(connectionId, schema, table);
    }

}