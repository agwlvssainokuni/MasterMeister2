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

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_audit_log_user_id", columnList = "userId"),
        @Index(name = "idx_audit_log_category_type", columnList = "eventCategory,eventType")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant occurredAt;

    private Long userId;

    private Long connectionId;

    @Enumerated(EnumType.STRING)
    private EventCategory eventCategory;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    private Result result;

    private String targetDescription;

    private String summaryMessage;

    protected AuditLog() {
    }

    public AuditLog(
            Instant occurredAt,
            Long userId,
            Long connectionId,
            EventCategory eventCategory,
            EventType eventType,
            Result result,
            String targetDescription,
            String summaryMessage
    ) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.connectionId = connectionId;
        this.eventCategory = eventCategory;
        this.eventType = eventType;
        this.result = result;
        this.targetDescription = targetDescription;
        this.summaryMessage = summaryMessage;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public EventCategory getEventCategory() {
        return eventCategory;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Result getResult() {
        return result;
    }

    public String getTargetDescription() {
        return targetDescription;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

}