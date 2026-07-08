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

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PageResult<AuditLogResponse> search(
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) EventCategory eventCategory,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int pageSize
    ) {
        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(
                dateFrom, dateTo, userId, eventCategory, eventType);
        PageResult<AuditLog> result = auditLogService.search(criteria, new PageRequest(page, pageSize));
        return new PageResult<>(
                result.content().stream().map(AuditLogResponse::from).toList(),
                result.totalCount(), result.page(), result.pageSize());
    }

}