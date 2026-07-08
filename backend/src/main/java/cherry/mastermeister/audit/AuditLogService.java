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

package cherry.mastermeister.audit;

import java.time.Instant;
import java.util.List;

import cherry.mastermeister.common.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final int defaultPageSize;
    private final List<Integer> pageSizeOptions;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            @Value("${mm.app.audit.default-page-size}") int defaultPageSize,
            @Value("${mm.app.audit.page-size-options}") List<Integer> pageSizeOptions
    ) {
        this.auditLogRepository = auditLogRepository;
        this.defaultPageSize = defaultPageSize;
        this.pageSizeOptions = pageSizeOptions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            EventCategory eventCategory,
            EventType eventType,
            Long userId,
            Long connectionId,
            Result result,
            String targetDescription,
            String summaryMessage
    ) {
        try {
            AuditLog auditLog = new AuditLog(
                    Instant.now(), userId, connectionId, eventCategory, eventType, result,
                    targetDescription, summaryMessage);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error(
                    "Failed to record audit log: eventCategory={}, eventType={}, userId={}",
                    eventCategory, eventType, userId, e);
        }
    }

    public PageResult<AuditLog> search(
            AuditLogFilterCriteria criteria,
            cherry.mastermeister.common.PageRequest pageRequest
    ) {
        int pageSize = resolvePageSize(pageRequest.pageSize());
        int page = Math.max(pageRequest.page(), 0);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLog> result = auditLogRepository.search(criteria, pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    private int resolvePageSize(int requestedPageSize) {
        if (pageSizeOptions.contains(requestedPageSize)) {
            return requestedPageSize;
        }
        return defaultPageSize;
    }

}