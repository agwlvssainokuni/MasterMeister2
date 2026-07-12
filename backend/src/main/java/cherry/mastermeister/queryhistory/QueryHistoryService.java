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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.savedquery.SavedQueryService;
import cherry.mastermeister.savedquery.SavedQueryStatus;

@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);

    private static final String MASKED_PLACEHOLDER = "(非公開のため表示できません)";

    private final QueryHistoryRepository queryHistoryRepository;
    private final SavedQueryService savedQueryService;
    private final int defaultPageSize;
    private final List<Integer> pageSizeOptions;

    public QueryHistoryService(
            QueryHistoryRepository queryHistoryRepository,
            SavedQueryService savedQueryService,
            @Value("${mm.app.query-history.default-page-size:50}") int defaultPageSize,
            @Value("${mm.app.query-history.page-size-options:50,100,200}") List<Integer> pageSizeOptions
    ) {
        this.queryHistoryRepository = queryHistoryRepository;
        this.savedQueryService = savedQueryService;
        this.defaultPageSize = defaultPageSize;
        this.pageSizeOptions = pageSizeOptions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExecution(ExecutionRecord record) {
        try {
            QueryHistory entity = new QueryHistory(
                    record.userId(), record.connectionId(), record.sql(), record.params(), record.resultCount(),
                    record.elapsedMillis(), record.executedAt(), record.savedQueryId(), record.savedQueryName(),
                    record.executionCount());
            queryHistoryRepository.save(entity);
        } catch (Exception e) {
            log.error(
                    "Failed to record query history: userId={}, connectionId={}, savedQueryId={}",
                    record.userId(), record.connectionId(), record.savedQueryId(), e);
        }
    }

    public PageResult<HistoryEntry> listHistory(
            Long userId, Long connectionId, HistoryFilterCriteria criteria, PageRequest page
    ) {
        int pageSize = resolvePageSize(page.pageSize());
        int pageNumber = Math.max(page.page(), 0);
        Long executorUserId = criteria.executorScope() == ExecutorScope.SELF ? userId : null;

        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageNumber, pageSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "executedAt"));
        Page<QueryHistory> result = queryHistoryRepository.search(
                connectionId, criteria.executedAtFrom(), criteria.executedAtTo(), executorUserId,
                criteria.sqlTextSearch(), pageable);

        List<QueryHistory> content = result.getContent();
        Set<Long> savedQueryIds = content.stream()
                .map(QueryHistory::getSavedQueryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SavedQueryStatus> statuses = savedQueryIds.isEmpty()
                ? Map.of() : savedQueryService.getStatuses(userId, savedQueryIds);

        List<HistoryEntry> entries = content.stream()
                .map(h -> toEntry(h, userId, statuses))
                .toList();

        return new PageResult<>(entries, result.getTotalElements(), pageNumber, pageSize);
    }

    private HistoryEntry toEntry(QueryHistory h, Long viewerId, Map<Long, SavedQueryStatus> statuses) {
        boolean ownRow = h.getUserId().equals(viewerId);
        SavedQueryStatus status = h.getSavedQueryId() == null ? null : statuses.get(h.getSavedQueryId());
        boolean masked = !ownRow && status != null && !status.visibleToViewer();
        boolean retired = status != null && status.retired();
        return new HistoryEntry(
                h.getId(), h.getUserId(), h.getConnectionId(),
                masked ? MASKED_PLACEHOLDER : h.getSql(),
                masked ? Map.of() : h.getParams(),
                h.getResultCount(), h.getElapsedMillis(), h.getExecutedAt(),
                h.getSavedQueryId(), masked ? MASKED_PLACEHOLDER : h.getSavedQueryName(),
                h.getExecutionCount(), retired, masked);
    }

    private int resolvePageSize(int requestedPageSize) {
        if (pageSizeOptions.contains(requestedPageSize)) {
            return requestedPageSize;
        }
        return defaultPageSize;
    }

}