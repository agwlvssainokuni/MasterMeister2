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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * example-basedテスト: 基本CRUD、{@code findByGroupIdAndUserId}/{@code findByGroupId}/
 * {@code findByUserId}/{@code deleteByGroupId}クエリメソッド、
 * {@code (groupId, userId)}のunique制約違反を検証する。
 */
@DataJpaTest
class GroupMemberRepositoryTest {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAssignsGeneratedId() {
        GroupMember saved = groupMemberRepository.saveAndFlush(
                new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));

        assertThat(saved.getId()).isNotNull();
        Optional<GroupMember> found = groupMemberRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        GroupMember saved = groupMemberRepository.saveAndFlush(
                new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));

        groupMemberRepository.delete(saved);
        groupMemberRepository.flush();

        assertThat(groupMemberRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByGroupIdAndUserIdReturnsMatchingMember() {
        entityManager.persistAndFlush(new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));

        Optional<GroupMember> found = groupMemberRepository.findByGroupIdAndUserId(1L, 7L);

        assertThat(found).isPresent();
        assertThat(found.get().getGroupId()).isEqualTo(1L);
        assertThat(found.get().getUserId()).isEqualTo(7L);
    }

    @Test
    void findByGroupIdAndUserIdReturnsEmptyWhenNoMatch() {
        Optional<GroupMember> found = groupMemberRepository.findByGroupIdAndUserId(1L, 999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findByGroupIdReturnsAllMembersOfGroup() {
        entityManager.persistAndFlush(new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));
        entityManager.persistAndFlush(new GroupMember(1L, 8L, Instant.ofEpochSecond(1_700_000_100L)));
        entityManager.persistAndFlush(new GroupMember(2L, 7L, Instant.ofEpochSecond(1_700_000_200L)));

        List<GroupMember> members = groupMemberRepository.findByGroupId(1L);

        assertThat(members).extracting(GroupMember::getUserId).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void findByUserIdReturnsAllGroupsOfUser() {
        entityManager.persistAndFlush(new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));
        entityManager.persistAndFlush(new GroupMember(2L, 7L, Instant.ofEpochSecond(1_700_000_100L)));
        entityManager.persistAndFlush(new GroupMember(1L, 8L, Instant.ofEpochSecond(1_700_000_200L)));

        List<GroupMember> memberships = groupMemberRepository.findByUserId(7L);

        assertThat(memberships).extracting(GroupMember::getGroupId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void deleteByGroupIdRemovesAllMembersOfGroup() {
        entityManager.persistAndFlush(new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));
        entityManager.persistAndFlush(new GroupMember(1L, 8L, Instant.ofEpochSecond(1_700_000_100L)));
        entityManager.persistAndFlush(new GroupMember(2L, 7L, Instant.ofEpochSecond(1_700_000_200L)));

        groupMemberRepository.deleteByGroupId(1L);
        entityManager.flush();

        assertThat(groupMemberRepository.findByGroupId(1L)).isEmpty();
        assertThat(groupMemberRepository.findByGroupId(2L)).hasSize(1);
    }

    @Test
    void savingDuplicateGroupIdUserIdViolatesUniqueConstraint() {
        entityManager.persistAndFlush(new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_000L)));

        assertThatThrownBy(() -> groupMemberRepository.saveAndFlush(
                new GroupMember(1L, 7L, Instant.ofEpochSecond(1_700_000_100L))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}