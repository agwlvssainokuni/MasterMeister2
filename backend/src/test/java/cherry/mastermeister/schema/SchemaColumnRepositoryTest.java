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

package cherry.mastermeister.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * example-basedテスト: 基本CRUD、{@code findByTableIdAndStaleFalse}/
 * {@code findByTableIdAndColumnName}/{@code findByTableId}クエリメソッド、
 * {@code (tableId, columnName)}のunique制約違反を検証する。
 */
@DataJpaTest
class SchemaColumnRepositoryTest {

    @Autowired
    private SchemaColumnRepository schemaColumnRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        SchemaColumn saved = schemaColumnRepository.saveAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThat(saved.getId()).isNotNull();
        Optional<SchemaColumn> found = schemaColumnRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        SchemaColumn saved = schemaColumnRepository.saveAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        schemaColumnRepository.delete(saved);
        schemaColumnRepository.flush();

        assertThat(schemaColumnRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByTableIdAndStaleFalseExcludesStaleColumns() {
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, false,
                Instant.ofEpochSecond(1_700_000_000L), null));
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "old_col", "VARCHAR", true, null, 2, null, true,
                Instant.ofEpochSecond(1_700_000_100L), null));

        List<SchemaColumn> columns = schemaColumnRepository.findByTableIdAndStaleFalse(1L);

        assertThat(columns).extracting(SchemaColumn::getColumnName).containsExactly("id");
    }

    @Test
    void findByTableIdAndColumnNameReturnsMatchingColumnRegardlessOfStale() {
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, true,
                Instant.ofEpochSecond(1_700_000_000L), null));

        Optional<SchemaColumn> found = schemaColumnRepository.findByTableIdAndColumnName(1L, "id");

        assertThat(found).isPresent();
        assertThat(found.get().isStale()).isTrue();
    }

    @Test
    void findByTableIdAndColumnNameReturnsEmptyWhenNoMatch() {
        Optional<SchemaColumn> found = schemaColumnRepository.findByTableIdAndColumnName(1L, "no-such-column");

        assertThat(found).isEmpty();
    }

    @Test
    void findByTableIdReturnsAllColumnsRegardlessOfStale() {
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, false,
                Instant.ofEpochSecond(1_700_000_000L), null));
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "old_col", "VARCHAR", true, null, 2, null, true,
                Instant.ofEpochSecond(1_700_000_100L), null));
        entityManager.persistAndFlush(new SchemaColumn(
                2L, "other_col", "INTEGER", false, null, 1, null, false,
                Instant.ofEpochSecond(1_700_000_200L), null));

        List<SchemaColumn> columns = schemaColumnRepository.findByTableId(1L);

        assertThat(columns).extracting(SchemaColumn::getColumnName)
                .containsExactlyInAnyOrder("id", "old_col");
    }

    @Test
    void savingDuplicateTableIdColumnNameViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new SchemaColumn(
                1L, "id", "BIGINT", false, null, 1, 1, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThatThrownBy(() -> schemaColumnRepository.saveAndFlush(new SchemaColumn(
                1L, "id", "VARCHAR", true, null, 2, null, false,
                Instant.ofEpochSecond(1_700_000_100L), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}