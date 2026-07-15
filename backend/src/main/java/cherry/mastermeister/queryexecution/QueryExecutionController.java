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

package cherry.mastermeister.queryexecution;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query-execution")
public class QueryExecutionController {

    private final QueryExecutionService queryExecutionService;

    public QueryExecutionController(QueryExecutionService queryExecutionService) {
        this.queryExecutionService = queryExecutionService;
    }

    @GetMapping("/{connectionId}/schemas")
    public List<String> listAccessibleSchemas(@PathVariable Long connectionId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return queryExecutionService.listAccessibleSchemas(userId, connectionId);
    }

    @PostMapping("/adhoc")
    public QueryResult executeAdhocSql(@RequestBody AdhocExecutionRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return queryExecutionService.executeAdhocSql(
                userId, request.connectionId(), request.schema(), request.sql(), request.params(),
                request.paging());
    }

    @PostMapping("/saved/{savedQueryId}")
    public QueryResult executeSavedQuery(
            @PathVariable Long savedQueryId, @RequestBody SavedExecutionRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return queryExecutionService.executeSavedQuery(
                userId, request.connectionId(), request.schema(), savedQueryId, request.params(),
                request.paging());
    }

}