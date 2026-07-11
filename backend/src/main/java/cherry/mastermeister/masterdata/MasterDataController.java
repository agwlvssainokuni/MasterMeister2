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

package cherry.mastermeister.masterdata;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cherry.mastermeister.common.PageRequest;

@RestController
@RequestMapping("/api/master-data/{connectionId}")
public class MasterDataController {

    private final MasterDataQueryService masterDataQueryService;
    private final MasterDataMutationService masterDataMutationService;

    public MasterDataController(
            MasterDataQueryService masterDataQueryService, MasterDataMutationService masterDataMutationService) {
        this.masterDataQueryService = masterDataQueryService;
        this.masterDataMutationService = masterDataMutationService;
    }

    @GetMapping("/schemas")
    public List<String> listAccessibleSchemas(@PathVariable Long connectionId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return masterDataQueryService.listAccessibleSchemas(userId, connectionId);
    }

    @GetMapping("/schemas/{schema}/tables")
    public List<TableSummary> listAccessibleTables(
            @PathVariable Long connectionId, @PathVariable String schema, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return masterDataQueryService.listAccessibleTables(userId, connectionId, schema);
    }

    @PostMapping("/schemas/{schema}/tables/{table}/records:search")
    public RecordListResult listRecords(
            @PathVariable Long connectionId, @PathVariable String schema, @PathVariable String table,
            @RequestBody RecordSearchRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PageRequest page = new PageRequest(request.page(), request.pageSize());
        return masterDataQueryService.listRecords(userId, connectionId, schema, table, request.criteria(), page);
    }

    @PostMapping("/schemas/{schema}/tables/{table}/records:apply")
    public MutationResult applyChanges(
            @PathVariable Long connectionId, @PathVariable String schema, @PathVariable String table,
            @RequestBody MutationRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return masterDataMutationService.applyChanges(userId, connectionId, schema, table, request);
    }

}
