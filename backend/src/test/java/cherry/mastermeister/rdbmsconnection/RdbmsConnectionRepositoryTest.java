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

package cherry.mastermeister.rdbmsconnection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cherry.mastermeister.common.dialect.RdbmsType;

/**
 * example-basedテスト: 基本CRUDを検証する（{@code RdbmsConnectionRepository}はカスタムクエリ
 * メソッド・unique制約を持たないため、CRUDのみ）。{@code password}が
 * {@code EncryptedStringConverter}経由で暗号化保存・復号読み出しされることも合わせて確認する。
 */
@DataJpaTest
class RdbmsConnectionRepositoryTest {

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    @Test
    void saveAssignsGeneratedId() {
        RdbmsConnection saved = rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection(
                "conn-1", RdbmsType.POSTGRESQL, "localhost", 5432, "mastermeister", "user",
                "secret", null, Instant.ofEpochSecond(1_700_000_000L), null));

        assertThat(saved.getId()).isNotNull();
        Optional<RdbmsConnection> found = rdbmsConnectionRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void savedPasswordRoundTripsThroughEncryptedStringConverter() {
        RdbmsConnection saved = rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection(
                "conn-1", RdbmsType.POSTGRESQL, "localhost", 5432, "mastermeister", "user",
                "secret", null, Instant.ofEpochSecond(1_700_000_000L), null));
        rdbmsConnectionRepository.flush();

        Optional<RdbmsConnection> found = rdbmsConnectionRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPassword()).isEqualTo("secret");
    }

    @Test
    void deleteRemovesEntity() {
        RdbmsConnection saved = rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection(
                "conn-1", RdbmsType.POSTGRESQL, "localhost", 5432, "mastermeister", "user",
                "secret", null, Instant.ofEpochSecond(1_700_000_000L), null));

        rdbmsConnectionRepository.delete(saved);
        rdbmsConnectionRepository.flush();

        assertThat(rdbmsConnectionRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findAllReturnsAllSavedConnections() {
        rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection(
                "conn-1", RdbmsType.MYSQL, "localhost", 3306, "db1", "user",
                "secret", null, Instant.ofEpochSecond(1_700_000_000L), null));
        rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection(
                "conn-2", RdbmsType.MARIADB, "localhost", 3307, "db2", "user",
                "secret", null, Instant.ofEpochSecond(1_700_000_100L), null));

        assertThat(rdbmsConnectionRepository.findAll()).extracting(RdbmsConnection::getName)
                .containsExactlyInAnyOrder("conn-1", "conn-2");
    }

}