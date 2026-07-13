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

package cherry.mastermeister.savedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.PermissionDeniedException;

/**
 * P1〜P3（business-logic-model.md）を検証するプロパティテスト。
 */
@JqwikSpringSupport
@DataJpaTest
class SavedQueryServiceTest {

    private static final long CONNECTION_ID = 1L;

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    // P1: 返される一覧は常にvisibility=PUBLICまたはownerId==呼び出しユーザのいずれかを満たす。
    @Property(tries = 20)
    void listQueriesReturnsOnlyPublicOrOwnQueries(
            @ForAll("savedQuerySeeds") List<SavedQuerySeed> seeds, @ForAll("viewerIds") long viewerId
    ) {
        savedQueryRepository.deleteAll();
        Instant now = Instant.now();
        List<SavedQuery> saved = seeds.stream()
                .map(seed -> savedQueryRepository.save(new SavedQuery(
                        seed.ownerId(), CONNECTION_ID, "q", "SELECT 1", seed.visibility(), seed.retired(), 0, now, now)))
                .toList();

        SavedQueryService service = new SavedQueryService(savedQueryRepository);
        List<SavedQuerySummary> result = service.listQueries(viewerId, CONNECTION_ID, true);

        Set<Long> expectedIds = new LinkedHashSet<>();
        for (int i = 0; i < seeds.size(); i++) {
            SavedQuerySeed seed = seeds.get(i);
            if (seed.visibility() == Visibility.PUBLIC || seed.ownerId() == viewerId) {
                expectedIds.add(saved.get(i).getId());
            }
        }

        assertThat(result).extracting(SavedQuerySummary::id).containsExactlyInAnyOrderElementsOf(expectedIds);
    }

    // P2: retired=trueのSavedQueryに対するupdateQuery/executeSavedQuery（getExecutableQuery）は
    //     常にEntityNotFoundException、getQueryは可視性条件のみで成否が決まる。
    @Property(tries = 20)
    void retiredQueryAlwaysRejectsExecutionAndUpdateRegardlessOfVisibility(
            @ForAll("viewerIds") long ownerId, @ForAll("viewerIds") long otherUserId,
            @ForAll("visibilities") Visibility visibility
    ) {
        savedQueryRepository.deleteAll();
        Instant now = Instant.now();
        SavedQuery entity = savedQueryRepository.save(new SavedQuery(
                ownerId, CONNECTION_ID, "q", "SELECT 1", visibility, true, 0, now, now));

        SavedQueryService service = new SavedQueryService(savedQueryRepository);

        assertThatThrownBy(() -> service.getExecutableQuery(ownerId, entity.getId()))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.getExecutableQuery(otherUserId, entity.getId()))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> service.updateQuery(ownerId, entity.getId(), "n", "SELECT 2", visibility))
                .isInstanceOf(EntityNotFoundException.class);

        boolean visibleToOther = visibility == Visibility.PUBLIC || ownerId == otherUserId;
        if (visibleToOther) {
            assertThat(service.getQuery(otherUserId, entity.getId()).retired()).isTrue();
        } else {
            assertThatThrownBy(() -> service.getQuery(otherUserId, entity.getId()))
                    .isInstanceOf(PermissionDeniedException.class);
        }
        assertThat(service.getQuery(ownerId, entity.getId()).retired()).isTrue();
    }

    // P3: 本ユニットのいかなる操作を経ても、一度retired=trueになったSavedQueryが
    //     retired=falseに戻ることは一切ない。
    @Property(tries = 15)
    void retireQueryNeverReversesOnSubsequentOperations(@ForAll("viewerIds") long ownerId) {
        savedQueryRepository.deleteAll();
        Instant now = Instant.now();
        SavedQuery entity = savedQueryRepository.save(new SavedQuery(
                ownerId, CONNECTION_ID, "q", "SELECT 1", Visibility.PUBLIC, false, 0, now, now));

        SavedQueryService service = new SavedQueryService(savedQueryRepository);
        service.retireQuery(ownerId, entity.getId());
        assertThat(service.getQuery(ownerId, entity.getId()).retired()).isTrue();

        assertThatThrownBy(() -> service.updateQuery(ownerId, entity.getId(), "n2", "SELECT 3", Visibility.PRIVATE))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(service.getQuery(ownerId, entity.getId()).retired()).isTrue();

        service.retireQuery(ownerId, entity.getId());
        assertThat(service.getQuery(ownerId, entity.getId()).retired()).isTrue();
    }

    private record SavedQuerySeed(long ownerId, Visibility visibility, boolean retired) {
    }

    @Provide
    Arbitrary<List<SavedQuerySeed>> savedQuerySeeds() {
        Arbitrary<Long> ownerId = Arbitraries.longs().between(1, 3);
        Arbitrary<Visibility> visibility = Arbitraries.of(Visibility.class);
        Arbitrary<Boolean> retired = Arbitraries.of(true, false);
        return Combinators.combine(ownerId, visibility, retired)
                .as(SavedQuerySeed::new)
                .list().ofMinSize(0).ofMaxSize(6);
    }

    @Provide
    Arbitrary<Long> viewerIds() {
        return Arbitraries.longs().between(1, 3);
    }

    @Provide
    Arbitrary<Visibility> visibilities() {
        return Arbitraries.of(Visibility.class);
    }

}