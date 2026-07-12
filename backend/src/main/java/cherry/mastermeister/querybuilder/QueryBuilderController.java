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

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query-builder/{connectionId}")
public class QueryBuilderController {

    private final QueryBuilderMetadataService queryBuilderMetadataService;
    private final SqlGenerationService sqlGenerationService;
    private final SqlParsingService sqlParsingService;

    public QueryBuilderController(
            QueryBuilderMetadataService queryBuilderMetadataService,
            SqlGenerationService sqlGenerationService,
            SqlParsingService sqlParsingService) {
        this.queryBuilderMetadataService = queryBuilderMetadataService;
        this.sqlGenerationService = sqlGenerationService;
        this.sqlParsingService = sqlParsingService;
    }

    @GetMapping("/schemas")
    public List<String> listSelectableSchemas(@PathVariable Long connectionId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return queryBuilderMetadataService.listSelectableSchemas(userId, connectionId);
    }

    @GetMapping("/schemas/{schema}/tables")
    public List<TableRef> listSelectableTables(
            @PathVariable Long connectionId, @PathVariable String schema, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return queryBuilderMetadataService.listSelectableTables(userId, connectionId, schema);
    }

    @GetMapping("/schemas/{schema}/tables/{table}/columns")
    public List<ColumnRef> listSelectableColumns(
            @PathVariable Long connectionId, @PathVariable String schema, @PathVariable String table,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return queryBuilderMetadataService.listSelectableColumns(userId, connectionId, schema, table);
    }

    @PostMapping("/generate")
    public GeneratedSql generate(@PathVariable Long connectionId, @RequestBody QueryBuilderModel model) {
        return sqlGenerationService.generate(connectionId, model);
    }

    @PostMapping("/parse")
    public ParseResult parse(
            @PathVariable Long connectionId, @RequestBody SqlParseRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return sqlParsingService.parse(userId, connectionId, request.rawSql());
    }

}