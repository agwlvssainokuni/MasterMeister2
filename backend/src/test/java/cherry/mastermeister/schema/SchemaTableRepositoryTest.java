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
 * example-basedテスト: 基本CRUD、{@code findByConnectionIdAndSchemaNameAndTableName}/
 * {@code findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse}/{@code findByConnectionId}/
 * {@code findByConnectionIdAndStaleFalse}/{@code findByConnectionIdAndSchemaNameAndStaleFalse}
 * クエリメソッド、{@code (connectionId, schemaName, tableName)}のunique制約違反を検証する。
 */
@DataJpaTest
class SchemaTableRepositoryTest {

    @Autowired
    private SchemaTableRepository schemaTableRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        SchemaTable saved = schemaTableRepository.saveAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThat(saved.getId()).isNotNull();
        Optional<SchemaTable> found = schemaTableRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        SchemaTable saved = schemaTableRepository.saveAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        schemaTableRepository.delete(saved);
        schemaTableRepository.flush();

        assertThat(schemaTableRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByConnectionIdAndSchemaNameAndTableNameReturnsMatchingTableRegardlessOfStale() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, true,
                Instant.ofEpochSecond(1_700_000_000L), null));

        Optional<SchemaTable> found = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(1L, "public", "users");

        assertThat(found).isPresent();
        assertThat(found.get().isStale()).isTrue();
    }

    @Test
    void findByConnectionIdAndSchemaNameAndTableNameReturnsEmptyWhenNoMatch() {
        Optional<SchemaTable> found = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableName(1L, "public", "no-such-table");

        assertThat(found).isEmpty();
    }

    @Test
    void findByConnectionIdAndSchemaNameAndTableNameAndStaleFalseExcludesStaleTable() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, true,
                Instant.ofEpochSecond(1_700_000_000L), null));

        Optional<SchemaTable> found = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse(1L, "public", "users");

        assertThat(found).isEmpty();
    }

    @Test
    void findByConnectionIdReturnsAllTablesRegardlessOfStale() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "orders", TableType.TABLE, null, true,
                Instant.ofEpochSecond(1_700_000_100L), null));
        entityManager.persistAndFlush(new SchemaTable(
                2L, "public", "other", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_200L), null));

        List<SchemaTable> tables = schemaTableRepository.findByConnectionId(1L);

        assertThat(tables).extracting(SchemaTable::getTableName)
                .containsExactlyInAnyOrder("users", "orders");
    }

    @Test
    void findByConnectionIdAndStaleFalseExcludesStaleTables() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "orders", TableType.TABLE, null, true,
                Instant.ofEpochSecond(1_700_000_100L), null));

        List<SchemaTable> tables = schemaTableRepository.findByConnectionIdAndStaleFalse(1L);

        assertThat(tables).extracting(SchemaTable::getTableName).containsExactly("users");
    }

    @Test
    void findByConnectionIdAndSchemaNameAndStaleFalseFiltersBySchemaAndExcludesStale() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));
        entityManager.persistAndFlush(new SchemaTable(
                1L, "audit", "logs", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_100L), null));
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "orders", TableType.TABLE, null, true,
                Instant.ofEpochSecond(1_700_000_200L), null));

        List<SchemaTable> tables = schemaTableRepository
                .findByConnectionIdAndSchemaNameAndStaleFalse(1L, "public");

        assertThat(tables).extracting(SchemaTable::getTableName).containsExactly("users");
    }

    @Test
    void savingDuplicateConnectionIdSchemaNameTableNameViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new SchemaTable(
                1L, "public", "users", TableType.TABLE, null, false,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThatThrownBy(() -> schemaTableRepository.saveAndFlush(new SchemaTable(
                1L, "public", "users", TableType.VIEW, null, false,
                Instant.ofEpochSecond(1_700_000_100L), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}