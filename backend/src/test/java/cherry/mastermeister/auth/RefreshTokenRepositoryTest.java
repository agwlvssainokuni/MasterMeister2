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

package cherry.mastermeister.auth;

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
 * example-basedテスト: 基本CRUD、{@code findByTokenHash}/{@code findByFamilyId}クエリメソッド、
 * {@code tokenHash}のunique制約違反を検証する。
 */
@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        RefreshToken saved = refreshTokenRepository.saveAndFlush(new RefreshToken(
                1L, "family-1", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        assertThat(saved.getId()).isNotNull();
        Optional<RefreshToken> found = refreshTokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        RefreshToken saved = refreshTokenRepository.saveAndFlush(new RefreshToken(
                1L, "family-1", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        refreshTokenRepository.delete(saved);
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByTokenHashReturnsMatchingToken() {
        entityManager.persistAndFlush(new RefreshToken(
                1L, "family-1", "token-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("token-hash");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void findByTokenHashReturnsEmptyWhenNoMatch() {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("no-such-hash");

        assertThat(found).isEmpty();
    }

    @Test
    void findByFamilyIdReturnsAllRowsInFamily() {
        entityManager.persistAndFlush(new RefreshToken(
                1L, "family-1", "hash-1", Instant.ofEpochSecond(1_700_003_600L),
                Instant.ofEpochSecond(1_700_001_000L), null, Instant.ofEpochSecond(1_700_000_000L)));
        entityManager.persistAndFlush(new RefreshToken(
                1L, "family-1", "hash-2", Instant.ofEpochSecond(1_700_007_200L),
                null, null, Instant.ofEpochSecond(1_700_001_000L)));
        entityManager.persistAndFlush(new RefreshToken(
                2L, "family-2", "hash-3", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        List<RefreshToken> family = refreshTokenRepository.findByFamilyId("family-1");

        assertThat(family).extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("hash-1", "hash-2");
    }

    @Test
    void findByFamilyIdReturnsEmptyWhenNoMatch() {
        List<RefreshToken> family = refreshTokenRepository.findByFamilyId("no-such-family");

        assertThat(family).isEmpty();
    }

    @Test
    void savingDuplicateTokenHashViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new RefreshToken(
                1L, "family-1", "duplicate-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_000L)));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(new RefreshToken(
                2L, "family-2", "duplicate-hash", Instant.ofEpochSecond(1_700_003_600L),
                null, null, Instant.ofEpochSecond(1_700_000_100L))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}