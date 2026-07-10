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

package cherry.mastermeister.permission;

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
 * example-basedテスト: 基本CRUD、
 * {@code findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType}/
 * {@code findByConnectionId}/{@code deleteByConnectionId}/
 * {@code deleteByPrincipalTypeAndPrincipalId}クエリメソッド、
 * {@code (principalType, principalId, connectionId, schemaName, tableName, auxType)}の
 * unique制約違反を検証する。
 */
@DataJpaTest
class AuxPermissionAssignmentRepositoryTest {

    @Autowired
    private AuxPermissionAssignmentRepository auxPermissionAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static AuxPermissionAssignment newAssignment(
            PrincipalType principalType, Long principalId, Long connectionId,
            String schemaName, String tableName, AuxPermissionType auxType, boolean granted) {
        return new AuxPermissionAssignment(
                principalType, principalId, connectionId, schemaName, tableName, auxType,
                granted, Instant.ofEpochSecond(1_700_000_000L));
    }

    @Test
    void saveAssignsGeneratedId() {
        AuxPermissionAssignment saved = auxPermissionAssignmentRepository.saveAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, true));

        assertThat(saved.getId()).isNotNull();
        Optional<AuxPermissionAssignment> found = auxPermissionAssignmentRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        AuxPermissionAssignment saved = auxPermissionAssignmentRepository.saveAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, true));

        auxPermissionAssignmentRepository.delete(saved);
        auxPermissionAssignmentRepository.flush();

        assertThat(auxPermissionAssignmentRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxTypeReturnsMatch() {
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.GROUP, 3L, 1L, "public", "employees", AuxPermissionType.CREATE, true));

        Optional<AuxPermissionAssignment> found = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        PrincipalType.GROUP, 3L, 1L, "public", "employees", AuxPermissionType.CREATE);

        assertThat(found).isPresent();
        assertThat(found.get().isGranted()).isTrue();
    }

    @Test
    void findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxTypeDistinguishesNullTable() {
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.GROUP, 3L, 1L, "public", null, AuxPermissionType.CREATE, true));

        Optional<AuxPermissionAssignment> schemaLevel = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        PrincipalType.GROUP, 3L, 1L, "public", null, AuxPermissionType.CREATE);
        Optional<AuxPermissionAssignment> tableLevel = auxPermissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(
                        PrincipalType.GROUP, 3L, 1L, "public", "employees", AuxPermissionType.CREATE);

        assertThat(schemaLevel).isPresent();
        assertThat(tableLevel).isEmpty();
    }

    @Test
    void findByConnectionIdReturnsAllAssignmentsOfConnection() {
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, true));
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.GROUP, 3L, 1L, "public", "orders", AuxPermissionType.CREATE, false));
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 7L, 2L, "public", "employees", AuxPermissionType.DELETE, true));

        List<AuxPermissionAssignment> assignments = auxPermissionAssignmentRepository.findByConnectionId(1L);

        assertThat(assignments).extracting(AuxPermissionAssignment::getTableName)
                .containsExactlyInAnyOrder("employees", "orders");
    }

    @Test
    void deleteByConnectionIdRemovesAllAssignmentsOfConnection() {
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, true));
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 7L, 2L, "public", "employees", AuxPermissionType.DELETE, true));

        auxPermissionAssignmentRepository.deleteByConnectionId(1L);
        entityManager.flush();

        assertThat(auxPermissionAssignmentRepository.findByConnectionId(1L)).isEmpty();
        assertThat(auxPermissionAssignmentRepository.findByConnectionId(2L)).hasSize(1);
    }

    @Test
    void deleteByPrincipalTypeAndPrincipalIdRemovesAllAssignmentsOfPrincipal() {
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.GROUP, 3L, 1L, "public", "employees", AuxPermissionType.DELETE, true));
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.GROUP, 3L, 2L, "public", "orders", AuxPermissionType.CREATE, true));
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 3L, 1L, "public", "employees", AuxPermissionType.DELETE, true));

        auxPermissionAssignmentRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, 3L);
        entityManager.flush();

        assertThat(auxPermissionAssignmentRepository.findByConnectionId(1L))
                .extracting(AuxPermissionAssignment::getPrincipalType)
                .containsExactly(PrincipalType.USER);
        assertThat(auxPermissionAssignmentRepository.findByConnectionId(2L)).isEmpty();
    }

    @Test
    void savingDuplicateKeyViolatesUniqueConstraint() {
        // tableNameはNULL許容カラムだが、unique制約検証はNULL同士が別値扱いされないよう
        // 非NULL値で固定した組み合わせを使う。
        entityManager.persistAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, true));

        assertThatThrownBy(() -> auxPermissionAssignmentRepository.saveAndFlush(newAssignment(
                PrincipalType.USER, 7L, 1L, "public", "employees", AuxPermissionType.DELETE, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}