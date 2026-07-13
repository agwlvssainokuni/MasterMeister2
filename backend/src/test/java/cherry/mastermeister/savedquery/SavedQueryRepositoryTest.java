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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * example-basedテスト: 基本CRUD、可視性フィルタクエリ（{@code findVisible}）、
 * {@code incrementExecutionCount}の並行実行時の整合性を検証する。P1〜P3は
 * {@code SavedQueryServiceTest}（jqwikによるproperty-basedテスト）で別途検証済み。
 */
@DataJpaTest
class SavedQueryRepositoryTest {

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void saveAssignsGeneratedId() {
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(new SavedQuery(
                1L, 42L, "q1", "SELECT 1", Visibility.PRIVATE, false, 0, now, now));

        assertThat(saved.getId()).isNotNull();
        Optional<SavedQuery> found = savedQueryRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(new SavedQuery(
                1L, 42L, "q1", "SELECT 1", Visibility.PRIVATE, false, 0, now, now));

        savedQueryRepository.delete(saved);
        savedQueryRepository.flush();

        assertThat(savedQueryRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findVisibleReturnsPublicAndOwnQueriesOrderedByName() {
        savedQueryRepository.deleteAll();
        Instant now = Instant.now();
        savedQueryRepository.saveAll(List.of(
                new SavedQuery(1L, 42L, "own", "SELECT 1", Visibility.PRIVATE, false, 0, now, now),
                new SavedQuery(2L, 42L, "public", "SELECT 2", Visibility.PUBLIC, false, 0, now, now),
                new SavedQuery(2L, 42L, "others-private", "SELECT 3", Visibility.PRIVATE, false, 0, now, now),
                new SavedQuery(1L, 42L, "retired-own", "SELECT 4", Visibility.PRIVATE, true, 0, now, now),
                new SavedQuery(1L, 99L, "other-connection", "SELECT 5", Visibility.PUBLIC, false, 0, now, now)
        ));

        List<SavedQuery> visible = savedQueryRepository.findVisible(42L, 1L, false);

        assertThat(visible).extracting(SavedQuery::getName).containsExactly("own", "public");
    }

    @Test
    void findVisibleIncludesRetiredOnlyWhenRequested() {
        savedQueryRepository.deleteAll();
        Instant now = Instant.now();
        savedQueryRepository.saveAll(List.of(
                new SavedQuery(1L, 42L, "own", "SELECT 1", Visibility.PRIVATE, false, 0, now, now),
                new SavedQuery(1L, 42L, "retired-own", "SELECT 4", Visibility.PRIVATE, true, 0, now, now)
        ));

        assertThat(savedQueryRepository.findVisible(42L, 1L, false))
                .extracting(SavedQuery::getName).containsExactly("own");
        assertThat(savedQueryRepository.findVisible(42L, 1L, true))
                .extracting(SavedQuery::getName).containsExactlyInAnyOrder("own", "retired-own");
    }

    // 並行実行下でincrementExecutionCountの加算漏れ（lost update）が発生しないことを検証する。
    // @DataJpaTestが既定で付与する外側トランザクションをPropagation.NOT_SUPPORTEDで無効化し、
    // saveAndFlush()の結果を各スレッドの別トランザクションから確実に参照できるようにしている
    // （SchemaImportServiceTestのRollbackRoundTripグループと同じ理由）。incrementExecutionCount
    // は@Modifying付きのカスタムクエリメソッドであり、SimpleJpaRepositoryの標準CRUDメソッドと
    // 異なりリポジトリ呼び出し自体は自動でトランザクションを開始しないため
    // （本番ではSavedQueryService.incrementExecutionCountの@Transactionalが担う）、
    // 各スレッドの呼び出しをTransactionTemplateで個別の実トランザクションとして包む。
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void incrementExecutionCountIsAtomicUnderConcurrentCalls() throws Exception {
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.saveAndFlush(new SavedQuery(
                1L, 42L, "q1", "SELECT 1", Visibility.PRIVATE, false, 0, now, now));
        Long id = saved.getId();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    transactionTemplate.executeWithoutResult(
                            status -> savedQueryRepository.incrementExecutionCount(id));
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        SavedQuery reloaded = savedQueryRepository.findById(id).orElseThrow();
        assertThat(reloaded.getExecutionCount()).isEqualTo(threadCount);

        savedQueryRepository.delete(reloaded);
    }

}