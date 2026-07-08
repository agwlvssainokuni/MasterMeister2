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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import cherry.mastermeister.common.PageResult;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * P1, P3〜P5（business-logic-model.md）を検証するプロパティテスト。
 * {@code AuditLogRepository} はStep 8で生成されるため、本テストはStep 8完了後にのみ
 * コンパイル・実行可能（グリーン確認はBuild and Testステージで行う）。
 */
@DataJpaTest
class AuditLogServiceTest {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(20, 50, 100);

    @Autowired
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeProperty
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS);
    }

    // P1: 内部DB書き込み失敗時も例外を伝播しない
    @Property
    void recordDoesNotPropagateExceptionOnRepositoryFailure(
            @ForAll("eventCategories") EventCategory eventCategory,
            @ForAll("eventTypes") EventType eventType,
            @ForAll("results") Result result,
            @ForAll("exceptions") RuntimeException thrown
    ) {
        AuditLogRepository failingRepository = mock(AuditLogRepository.class);
        when(failingRepository.save(any())).thenThrow(thrown);
        AuditLogService service = new AuditLogService(failingRepository, DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS);

        assertThatCode(() -> service.record(
                eventCategory, eventType, 1L, null, result, "target", "summary"))
                .doesNotThrowAnyException();
    }

    // P3〜P5: フィルタ正当性、occurredAt降順整列、ページサイズ上限
    @Property
    void searchReturnsOnlyMatchingResultsSortedDescendingWithBoundedPageSize(
            @ForAll("auditLogSets") List<AuditLog> auditLogs,
            @ForAll("filterCriteria") AuditLogFilterCriteria criteria,
            @ForAll("requestedPageSizes") int requestedPageSize
    ) {
        auditLogRepository.deleteAll();
        auditLogRepository.saveAll(auditLogs);

        PageResult<AuditLog> result = auditLogService.search(
                criteria, new cherry.mastermeister.common.PageRequest(0, requestedPageSize));

        assertThat(result.content()).allSatisfy(log -> {
            if (criteria.dateFrom() != null) {
                assertThat(log.getOccurredAt()).isAfterOrEqualTo(criteria.dateFrom());
            }
            if (criteria.dateTo() != null) {
                assertThat(log.getOccurredAt()).isBeforeOrEqualTo(criteria.dateTo());
            }
            if (criteria.userId() != null) {
                assertThat(log.getUserId()).isEqualTo(criteria.userId());
            }
            if (criteria.eventCategory() != null) {
                assertThat(log.getEventCategory()).isEqualTo(criteria.eventCategory());
            }
            if (criteria.eventType() != null) {
                assertThat(log.getEventType()).isEqualTo(criteria.eventType());
            }
        });

        assertThat(result.content())
                .isSortedAccordingTo((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));

        int expectedPageSize = PAGE_SIZE_OPTIONS.contains(requestedPageSize)
                ? requestedPageSize : DEFAULT_PAGE_SIZE;
        assertThat(result.pageSize()).isEqualTo(expectedPageSize);
        assertThat(result.content().size()).isLessThanOrEqualTo(expectedPageSize);
    }

    @Provide
    Arbitrary<List<AuditLog>> auditLogSets() {
        return auditLogArbitrary().list().ofMinSize(0).ofMaxSize(15);
    }

    private Arbitrary<AuditLog> auditLogArbitrary() {
        Arbitrary<Instant> occurredAt = Arbitraries.longs()
                .between(1_700_000_000L, 1_900_000_000L)
                .map(Instant::ofEpochSecond);
        Arbitrary<Long> userId = Arbitraries.longs().between(1, 5);
        Arbitrary<EventCategory> eventCategory = Arbitraries.of(EventCategory.class);
        Arbitrary<EventType> eventType = Arbitraries.of(EventType.class);
        Arbitrary<Result> result = Arbitraries.of(Result.class);

        return Combinators.combine(occurredAt, userId, eventCategory, eventType, result)
                .as((ts, uid, cat, type, res) -> new AuditLog(
                        ts, uid, null, cat, type, res, "target", "summary"));
    }

    @Provide
    Arbitrary<AuditLogFilterCriteria> filterCriteria() {
        Arbitrary<Instant> dateFrom = Arbitraries.longs()
                .between(1_700_000_000L, 1_800_000_000L)
                .map(Instant::ofEpochSecond).injectNull(0.5);
        Arbitrary<Instant> dateTo = Arbitraries.longs()
                .between(1_800_000_001L, 1_900_000_000L)
                .map(Instant::ofEpochSecond).injectNull(0.5);
        Arbitrary<Long> userId = Arbitraries.longs().between(1, 5).injectNull(0.5);
        Arbitrary<EventCategory> eventCategory = Arbitraries.of(EventCategory.class).injectNull(0.5);
        Arbitrary<EventType> eventType = Arbitraries.of(EventType.class).injectNull(0.5);

        return Combinators.combine(dateFrom, dateTo, userId, eventCategory, eventType)
                .as(AuditLogFilterCriteria::new);
    }

    @Provide
    Arbitrary<Integer> requestedPageSizes() {
        return Arbitraries.integers().between(0, 200);
    }

    @Provide
    Arbitrary<EventCategory> eventCategories() {
        return Arbitraries.of(EventCategory.class);
    }

    @Provide
    Arbitrary<EventType> eventTypes() {
        return Arbitraries.of(EventType.class);
    }

    @Provide
    Arbitrary<Result> results() {
        return Arbitraries.of(Result.class);
    }

    @Provide
    Arbitrary<RuntimeException> exceptions() {
        return Arbitraries.of(
                new RuntimeException("db error"),
                new IllegalStateException("illegal state"),
                new org.springframework.dao.DataAccessResourceFailureException("resource failure"));
    }

}