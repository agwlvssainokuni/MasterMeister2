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

package cherry.mastermeister.userregistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * example-basedテスト: 基本CRUD、{@code findByTokenHash}/
 * {@code findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull}クエリメソッド、
 * {@code tokenHash}のunique制約違反を検証する。
 */
@DataJpaTest
class RegistrationTokenRepositoryTest {

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        RegistrationToken saved = registrationTokenRepository.saveAndFlush(new RegistrationToken(
                "user@example.com", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        assertThat(saved.getId()).isNotNull();
        Optional<RegistrationToken> found = registrationTokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        RegistrationToken saved = registrationTokenRepository.saveAndFlush(new RegistrationToken(
                "user@example.com", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        registrationTokenRepository.delete(saved);
        registrationTokenRepository.flush();

        assertThat(registrationTokenRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByTokenHashReturnsMatchingToken() {
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        Optional<RegistrationToken> found = registrationTokenRepository.findByTokenHash("token-hash");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNoMatch() {
        Optional<RegistrationToken> found = registrationTokenRepository.findByTokenHash("no-such-hash");

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNullReturnsOnlyActiveToken() {
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "consumed-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, Instant.ofEpochSecond(1_700_001_000L), Instant.ofEpochSecond(1_700_000_000L)));
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "invalidated-hash", Instant.ofEpochSecond(1_700_003_600L),
                Instant.ofEpochSecond(1_700_001_000L), null, Instant.ofEpochSecond(1_700_000_050L)));
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "active-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_100L)));

        Optional<RegistrationToken> found = registrationTokenRepository
                .findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo("active-hash");
    }

    @Test
    void findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNullReturnsEmptyWhenNoneActive() {
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "consumed-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, Instant.ofEpochSecond(1_700_001_000L), Instant.ofEpochSecond(1_700_000_000L)));

        Optional<RegistrationToken> found = registrationTokenRepository
                .findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull("user@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void savingDuplicateTokenHashViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new RegistrationToken(
                "user@example.com", "duplicate-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        assertThatThrownBy(() -> registrationTokenRepository.saveAndFlush(new RegistrationToken(
                "other@example.com", "duplicate-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_100L))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}