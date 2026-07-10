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

package cherry.mastermeister.group;

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
 * example-basedテスト: 基本CRUD、{@code findByName}クエリメソッド、
 * {@code name}のunique制約違反を検証する。
 */
@DataJpaTest
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        Group saved = groupRepository.saveAndFlush(
                new Group("team-a", Instant.ofEpochSecond(1_700_000_000L)));

        assertThat(saved.getId()).isNotNull();
        Optional<Group> found = groupRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        Group saved = groupRepository.saveAndFlush(
                new Group("team-a", Instant.ofEpochSecond(1_700_000_000L)));

        groupRepository.delete(saved);
        groupRepository.flush();

        assertThat(groupRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByNameReturnsMatchingGroup() {
        entityManager.persistAndFlush(new Group("team-a", Instant.ofEpochSecond(1_700_000_000L)));

        Optional<Group> found = groupRepository.findByName("team-a");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("team-a");
    }

    @Test
    void findByNameReturnsEmptyWhenNoMatch() {
        Optional<Group> found = groupRepository.findByName("no-such-group");

        assertThat(found).isEmpty();
    }

    @Test
    void savingDuplicateNameViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new Group("team-a", Instant.ofEpochSecond(1_700_000_000L)));

        assertThatThrownBy(() -> groupRepository.saveAndFlush(
                new Group("team-a", Instant.ofEpochSecond(1_700_000_100L))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}