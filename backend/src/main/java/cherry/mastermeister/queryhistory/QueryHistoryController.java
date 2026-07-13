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

package cherry.mastermeister.queryhistory;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;

@RestController
@RequestMapping("/api/query-history")
public class QueryHistoryController {

    private final QueryHistoryService queryHistoryService;

    public QueryHistoryController(QueryHistoryService queryHistoryService) {
        this.queryHistoryService = queryHistoryService;
    }

    @GetMapping
    public PageResult<HistoryEntry> listHistory(
            @RequestParam Long connectionId,
            @RequestParam(required = false) Instant executedAtFrom,
            @RequestParam(required = false) Instant executedAtTo,
            @RequestParam(defaultValue = "ALL") ExecutorScope executorScope,
            @RequestParam(required = false) String sqlTextSearch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int pageSize,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        HistoryFilterCriteria criteria = new HistoryFilterCriteria(
                executedAtFrom, executedAtTo, executorScope, sqlTextSearch);
        return queryHistoryService.listHistory(userId, connectionId, criteria, new PageRequest(page, pageSize));
    }

}