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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cherry.mastermeister.common.PageRequest;
import cherry.mastermeister.common.PageResult;
import cherry.mastermeister.savedquery.SavedQuery;
import cherry.mastermeister.savedquery.SavedQueryRepository;
import cherry.mastermeister.savedquery.SavedQueryService;
import cherry.mastermeister.savedquery.Visibility;

/**
 * P9・P10（business-logic-model.md）を検証するプロパティテスト。
 */
@JqwikSpringSupport
@DataJpaTest
class QueryHistoryServiceTest {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(50, 100, 200);
    private static final String MASKED_PLACEHOLDER = "(非公開のため表示できません)";
    private static final long CONNECTION_ID = 1L;

    @Autowired
    private QueryHistoryRepository queryHistoryRepository;

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    // P9: masked=trueとなるのは、savedQueryId非nullかつ自分の実行行ではない行のうち、
    //     参照先SavedQueryが「retired=falseかつ（visibility=PUBLICまたはownerId==閲覧者）」を
    //     満たさない場合に限る。
    // P10: 「廃止済み」バッジは、同じ行のマスキング有無とは独立に、参照先SavedQuery.retiredの
    //     値のみで決まる。
    @Property(tries = 20)
    void listHistoryMasksAndBadgesIndependentlyPerRow(
            @ForAll("savedQuerySeeds") List<SavedQuerySeed> savedQuerySeeds,
            @ForAll("historySeeds") List<HistorySeed> historySeeds,
            @ForAll("viewerIds") long viewerId
    ) {
        queryHistoryRepository.deleteAll();
        savedQueryRepository.deleteAll();

        Instant now = Instant.now();
        List<SavedQuery> savedQueries = savedQuerySeeds.stream()
                .map(seed -> savedQueryRepository.save(new SavedQuery(
                        seed.ownerId(), CONNECTION_ID, "q", "SELECT 1", seed.visibility(), seed.retired(), 0, now, now)))
                .toList();

        for (int i = 0; i < historySeeds.size(); i++) {
            HistorySeed seed = historySeeds.get(i);
            Long savedQueryId = null;
            String savedQueryName = null;
            Integer executionCount = null;
            if (seed.savedQueryIndex() >= 0 && seed.savedQueryIndex() < savedQueries.size()) {
                savedQueryId = savedQueries.get(seed.savedQueryIndex()).getId();
                savedQueryName = "name-" + savedQueryId;
                executionCount = 1;
            }
            queryHistoryRepository.save(new QueryHistory(
                    seed.userId(), CONNECTION_ID, "S1", "SELECT " + i, Map.of(), 1, 10L,
                    now.plusSeconds(i), savedQueryId, savedQueryName, executionCount));
        }

        SavedQueryService savedQueryService = new SavedQueryService(savedQueryRepository);
        QueryHistoryService service = new QueryHistoryService(
                queryHistoryRepository, savedQueryService, DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS);

        PageResult<HistoryEntry> page = service.listHistory(
                viewerId, CONNECTION_ID, new HistoryFilterCriteria(null, null, ExecutorScope.ALL, null),
                new PageRequest(0, DEFAULT_PAGE_SIZE));

        for (HistoryEntry entry : page.content()) {
            if (entry.savedQueryId() == null) {
                assertThat(entry.masked()).isFalse();
                assertThat(entry.retired()).isFalse();
                continue;
            }
            SavedQuery referenced = savedQueries.stream()
                    .filter(sq -> sq.getId().equals(entry.savedQueryId()))
                    .findFirst().orElseThrow();
            boolean ownRow = entry.userId() == viewerId;
            boolean visibleToViewer = referenced.getVisibility() == Visibility.PUBLIC
                    || referenced.getOwnerId() == viewerId;
            boolean expectedMasked = !ownRow && !visibleToViewer;
            boolean expectedRetired = referenced.isRetired();

            assertThat(entry.masked()).isEqualTo(expectedMasked);
            if (expectedMasked) {
                assertThat(entry.sql()).isEqualTo(MASKED_PLACEHOLDER);
                assertThat(entry.savedQueryName()).isEqualTo(MASKED_PLACEHOLDER);
                assertThat(entry.params()).isEmpty();
            } else {
                assertThat(entry.sql()).isNotEqualTo(MASKED_PLACEHOLDER);
            }

            assertThat(entry.retired()).isEqualTo(expectedRetired);
        }
    }

    private record SavedQuerySeed(long ownerId, Visibility visibility, boolean retired) {
    }

    private record HistorySeed(long userId, int savedQueryIndex) {
    }

    @Provide
    Arbitrary<List<SavedQuerySeed>> savedQuerySeeds() {
        Arbitrary<Long> ownerId = Arbitraries.longs().between(1, 3);
        Arbitrary<Visibility> visibility = Arbitraries.of(Visibility.class);
        Arbitrary<Boolean> retired = Arbitraries.of(true, false);
        return Combinators.combine(ownerId, visibility, retired)
                .as(SavedQuerySeed::new)
                .list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<HistorySeed>> historySeeds() {
        Arbitrary<Long> userId = Arbitraries.longs().between(1, 3);
        Arbitrary<Integer> savedQueryIndex = Arbitraries.integers().between(-1, 2);
        return Combinators.combine(userId, savedQueryIndex)
                .as(HistorySeed::new)
                .list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<Long> viewerIds() {
        return Arbitraries.longs().between(1, 3);
    }

}