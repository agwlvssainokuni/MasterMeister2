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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * P2（domain-entities.md, business-logic-model.md）を検証するプロパティテストと、
 * 基本CRUD・フィルタクエリのexample-basedテスト。
 */
@JqwikSpringSupport
@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    // P2: 書き込んだAuditLogの全フィールドが読み出しで同一値として得られる（Round-trip）
    @Property
    void savedAuditLogRoundTripsAllFieldsOnReload(@ForAll("auditLogs") AuditLog auditLog) {
        AuditLog reloaded = entityManager.persistFlushFind(auditLog);

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getOccurredAt()).isEqualTo(auditLog.getOccurredAt());
        assertThat(reloaded.getUserId()).isEqualTo(auditLog.getUserId());
        assertThat(reloaded.getConnectionId()).isEqualTo(auditLog.getConnectionId());
        assertThat(reloaded.getEventCategory()).isEqualTo(auditLog.getEventCategory());
        assertThat(reloaded.getEventType()).isEqualTo(auditLog.getEventType());
        assertThat(reloaded.getResult()).isEqualTo(auditLog.getResult());
        assertThat(reloaded.getTargetDescription()).isEqualTo(auditLog.getTargetDescription());
        assertThat(reloaded.getSummaryMessage()).isEqualTo(auditLog.getSummaryMessage());
    }

    @Test
    void saveAssignsGeneratedId() {
        AuditLog saved = auditLogRepository.saveAndFlush(new AuditLog(
                Instant.ofEpochSecond(1_700_000_000L), 1L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, Result.SUCCESS,
                "user@example.com", null));

        assertThat(saved.getId()).isNotNull();
        Optional<AuditLog> found = auditLogRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        AuditLog saved = auditLogRepository.saveAndFlush(new AuditLog(
                Instant.ofEpochSecond(1_700_000_000L), 1L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_FAILURE, Result.FAILURE,
                "user@example.com", "bad credentials"));

        auditLogRepository.delete(saved);
        auditLogRepository.flush();

        assertThat(auditLogRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void searchReturnsAllRowsDescendingByOccurredAtWhenCriteriaAllNull() {
        auditLogRepository.deleteAll();
        auditLogRepository.saveAll(List.of(
                new AuditLog(Instant.ofEpochSecond(1_700_000_000L), 1L, null,
                        EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, Result.SUCCESS, "a", null),
                new AuditLog(Instant.ofEpochSecond(1_700_000_200L), 2L, null,
                        EventCategory.ADMIN_OPERATION, EventType.USER_REGISTRATION_APPROVED, Result.SUCCESS, "b", null),
                new AuditLog(Instant.ofEpochSecond(1_700_000_100L), 3L, null,
                        EventCategory.DATA_ACCESS, EventType.LOGIN_FAILURE, Result.FAILURE, "c", null)
        ));

        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(null, null, null, null, null);
        Page<AuditLog> page = auditLogRepository.search(
                criteria, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt")));

        assertThat(page.getContent()).extracting(AuditLog::getTargetDescription)
                .containsExactly("b", "c", "a");
    }

    @Test
    void searchFiltersByUserIdEventCategoryEventTypeAndDateRange() {
        auditLogRepository.deleteAll();
        AuditLog matching = new AuditLog(Instant.ofEpochSecond(1_700_000_100L), 1L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, Result.SUCCESS, "match", null);
        AuditLog differentUser = new AuditLog(Instant.ofEpochSecond(1_700_000_100L), 2L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, Result.SUCCESS, "different-user", null);
        AuditLog differentCategory = new AuditLog(Instant.ofEpochSecond(1_700_000_100L), 1L, null,
                EventCategory.DATA_ACCESS, EventType.LOGIN_SUCCESS, Result.SUCCESS, "different-category", null);
        AuditLog differentType = new AuditLog(Instant.ofEpochSecond(1_700_000_100L), 1L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_FAILURE, Result.FAILURE, "different-type", null);
        AuditLog outOfRange = new AuditLog(Instant.ofEpochSecond(1_600_000_000L), 1L, null,
                EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS, Result.SUCCESS, "out-of-range", null);
        auditLogRepository.saveAll(List.of(matching, differentUser, differentCategory, differentType, outOfRange));

        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(
                Instant.ofEpochSecond(1_699_999_000L), Instant.ofEpochSecond(1_700_001_000L),
                1L, EventCategory.AUTHENTICATION, EventType.LOGIN_SUCCESS);
        Page<AuditLog> page = auditLogRepository.search(
                criteria, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt")));

        assertThat(page.getContent()).extracting(AuditLog::getTargetDescription)
                .containsExactly("match");
    }

    @Provide
    Arbitrary<AuditLog> auditLogs() {
        Arbitrary<Instant> occurredAt = Arbitraries.longs()
                .between(0L, 4_102_444_800L)
                .map(Instant::ofEpochSecond);
        Arbitrary<Long> userId = Arbitraries.longs().between(1, Long.MAX_VALUE).injectNull(0.3);
        Arbitrary<Long> connectionId = Arbitraries.longs().between(1, Long.MAX_VALUE).injectNull(0.3);
        Arbitrary<EventCategory> eventCategory = Arbitraries.of(EventCategory.class);
        Arbitrary<EventType> eventType = Arbitraries.of(EventType.class);
        Arbitrary<Result> result = Arbitraries.of(Result.class);
        Arbitrary<String> targetDescription = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(0).ofMaxLength(50).injectNull(0.3);
        Arbitrary<String> summaryMessage = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(0).ofMaxLength(200).injectNull(0.3);

        return Combinators.combine(
                occurredAt, userId, connectionId, eventCategory, eventType, result,
                targetDescription, summaryMessage
        ).as(AuditLog::new);
    }

}