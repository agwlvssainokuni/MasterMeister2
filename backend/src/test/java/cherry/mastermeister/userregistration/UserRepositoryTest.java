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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * example-basedテスト: 基本CRUD、{@code findByEmail}/{@code countByRole}/
 * {@code findByStatusOrderByCreatedAtAsc}クエリメソッド、{@code email}のunique制約違反を検証する。
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        User saved = userRepository.saveAndFlush(new User(
                "user@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThat(saved.getId()).isNotNull();
        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        User saved = userRepository.saveAndFlush(new User(
                "user@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_000L), null));

        userRepository.delete(saved);
        userRepository.flush();

        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByEmailReturnsMatchingUser() {
        entityManager.persistAndFlush(new User(
                "user@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_000L), null));

        Optional<User> found = userRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void findByEmailReturnsEmptyWhenNoMatch() {
        Optional<User> found = userRepository.findByEmail("nobody@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void countByRoleCountsOnlyMatchingRole() {
        entityManager.persistAndFlush(new User(
                "admin1@example.com", "hash", Role.ADMIN, UserStatus.APPROVED,
                Instant.ofEpochSecond(1_700_000_000L), Instant.ofEpochSecond(1_700_000_100L)));
        entityManager.persistAndFlush(new User(
                "admin2@example.com", "hash", Role.ADMIN, UserStatus.APPROVED,
                Instant.ofEpochSecond(1_700_000_000L), Instant.ofEpochSecond(1_700_000_100L)));
        entityManager.persistAndFlush(new User(
                "user1@example.com", "hash", Role.USER, UserStatus.APPROVED,
                Instant.ofEpochSecond(1_700_000_000L), Instant.ofEpochSecond(1_700_000_100L)));

        assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(2L);
        assertThat(userRepository.countByRole(Role.USER)).isEqualTo(1L);
    }

    @Test
    void findByStatusOrderByCreatedAtAscReturnsOnlyMatchingStatusInAscendingOrder() {
        entityManager.persistAndFlush(new User(
                "pending2@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_200L), null));
        entityManager.persistAndFlush(new User(
                "approved@example.com", "hash", Role.USER, UserStatus.APPROVED,
                Instant.ofEpochSecond(1_700_000_050L), Instant.ofEpochSecond(1_700_000_300L)));
        entityManager.persistAndFlush(new User(
                "pending1@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_100L), null));

        List<User> pending = userRepository.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_APPROVAL);

        assertThat(pending).extracting(User::getEmail)
                .containsExactly("pending1@example.com", "pending2@example.com");
    }

    @Test
    void savingDuplicateEmailViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new User(
                "duplicate@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_000L), null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User(
                "duplicate@example.com", "hash", Role.USER, UserStatus.PENDING_APPROVAL,
                Instant.ofEpochSecond(1_700_000_100L), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}