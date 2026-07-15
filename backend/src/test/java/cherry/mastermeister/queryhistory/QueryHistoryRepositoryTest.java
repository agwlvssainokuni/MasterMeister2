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
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * example-basedテスト: 基本CRUD、{@code search}クエリメソッド（絞り込み・ページング・
 * 日時範囲）を検証する。P9・P10は{@code QueryHistoryServiceTest}
 * （jqwikによるproperty-basedテスト）で別途検証済み。
 */
@DataJpaTest
class QueryHistoryRepositoryTest {

    @Autowired
    private QueryHistoryRepository queryHistoryRepository;

    private static final String SCHEMA = "S1";

    @Test
    void saveAssignsGeneratedId() {
        QueryHistory saved = queryHistoryRepository.saveAndFlush(new QueryHistory(
                1L, 42L, SCHEMA, "SELECT 1", Map.of(), 1, 10L, Instant.ofEpochSecond(1_700_000_000L),
                null, null, null));

        assertThat(saved.getId()).isNotNull();
        Optional<QueryHistory> found = queryHistoryRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void savedParamsRoundTripThroughJsonMapConverter() {
        QueryHistory saved = queryHistoryRepository.saveAndFlush(new QueryHistory(
                1L, 42L, SCHEMA, "SELECT :id", Map.of("id", 1), 1, 10L, Instant.ofEpochSecond(1_700_000_000L),
                null, null, null));
        queryHistoryRepository.flush();

        Optional<QueryHistory> found = queryHistoryRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getParams()).containsEntry("id", 1);
    }

    @Test
    void deleteRemovesEntity() {
        QueryHistory saved = queryHistoryRepository.saveAndFlush(new QueryHistory(
                1L, 42L, SCHEMA, "SELECT 1", Map.of(), 1, 10L, Instant.ofEpochSecond(1_700_000_000L),
                null, null, null));

        queryHistoryRepository.delete(saved);
        queryHistoryRepository.flush();

        assertThat(queryHistoryRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void searchReturnsAllRowsForConnectionDescendingByExecutedAtWhenOtherCriteriaAreNull() {
        queryHistoryRepository.deleteAll();
        queryHistoryRepository.saveAll(List.of(
                new QueryHistory(1L, 42L, SCHEMA, "SELECT a", Map.of(), 1, 10L,
                        Instant.ofEpochSecond(1_700_000_000L), null, null, null),
                new QueryHistory(2L, 42L, SCHEMA, "SELECT b", Map.of(), 1, 10L,
                        Instant.ofEpochSecond(1_700_000_200L), null, null, null),
                new QueryHistory(3L, 42L, SCHEMA, "SELECT c", Map.of(), 1, 10L,
                        Instant.ofEpochSecond(1_700_000_100L), null, null, null),
                new QueryHistory(1L, 99L, SCHEMA, "SELECT d", Map.of(), 1, 10L,
                        Instant.ofEpochSecond(1_700_000_300L), null, null, null)
        ));

        Page<QueryHistory> page = queryHistoryRepository.search(
                42L, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "executedAt")));

        assertThat(page.getContent()).extracting(QueryHistory::getSql).containsExactly("SELECT b", "SELECT c", "SELECT a");
    }

    @Test
    void searchFiltersByDateRangeUserIdAndSqlTextSearch() {
        queryHistoryRepository.deleteAll();
        QueryHistory matching = new QueryHistory(1L, 42L, SCHEMA, "SELECT match FROM tbl", Map.of(), 1, 10L,
                Instant.ofEpochSecond(1_700_000_100L), null, null, null);
        QueryHistory differentUser = new QueryHistory(2L, 42L, SCHEMA, "SELECT match FROM tbl", Map.of(), 1, 10L,
                Instant.ofEpochSecond(1_700_000_100L), null, null, null);
        QueryHistory differentSql = new QueryHistory(1L, 42L, SCHEMA, "SELECT other FROM tbl", Map.of(), 1, 10L,
                Instant.ofEpochSecond(1_700_000_100L), null, null, null);
        QueryHistory outOfRange = new QueryHistory(1L, 42L, SCHEMA, "SELECT match FROM tbl", Map.of(), 1, 10L,
                Instant.ofEpochSecond(1_600_000_000L), null, null, null);
        queryHistoryRepository.saveAll(List.of(matching, differentUser, differentSql, outOfRange));

        Page<QueryHistory> page = queryHistoryRepository.search(
                42L, Instant.ofEpochSecond(1_699_999_000L), Instant.ofEpochSecond(1_700_001_000L), 1L, "match",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "executedAt")));

        assertThat(page.getContent()).extracting(QueryHistory::getSql).containsExactly("SELECT match FROM tbl");
    }

    @Test
    void searchIsCaseInsensitiveOnSqlTextSearch() {
        queryHistoryRepository.deleteAll();
        queryHistoryRepository.save(new QueryHistory(1L, 42L, SCHEMA, "SELECT * FROM Employees", Map.of(), 1, 10L,
                Instant.ofEpochSecond(1_700_000_100L), null, null, null));

        Page<QueryHistory> page = queryHistoryRepository.search(
                42L, null, null, null, "employees",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "executedAt")));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void searchRespectsPageSize() {
        queryHistoryRepository.deleteAll();
        for (int i = 0; i < 5; i++) {
            queryHistoryRepository.save(new QueryHistory(1L, 42L, SCHEMA, "SELECT " + i, Map.of(), 1, 10L,
                    Instant.ofEpochSecond(1_700_000_000L + i), null, null, null));
        }

        Page<QueryHistory> page = queryHistoryRepository.search(
                42L, null, null, null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "executedAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

}