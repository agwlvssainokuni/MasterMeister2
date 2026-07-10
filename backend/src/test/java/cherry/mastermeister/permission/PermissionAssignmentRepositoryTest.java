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
 * {@code findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName}/
 * {@code findByConnectionId}/{@code deleteByConnectionId}/
 * {@code deleteByPrincipalTypeAndPrincipalId}クエリメソッド、
 * {@code (principalType, principalId, connectionId, schemaName, tableName, columnName)}の
 * unique制約違反を検証する。
 */
@DataJpaTest
class PermissionAssignmentRepositoryTest {

    @Autowired
    private PermissionAssignmentRepository permissionAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static PermissionAssignment newAssignment(
            PrincipalType principalType, Long principalId, Long connectionId,
            String schemaName, String tableName, String columnName, Permission permission) {
        return new PermissionAssignment(
                principalType, principalId, connectionId, schemaName, tableName, columnName,
                permission, Instant.ofEpochSecond(1_700_000_000L));
    }

    @Test
    void saveAssignsGeneratedId() {
        PermissionAssignment saved = permissionAssignmentRepository.saveAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", null, Permission.READ));

        assertThat(saved.getId()).isNotNull();
        Optional<PermissionAssignment> found = permissionAssignmentRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void deleteRemovesEntity() {
        PermissionAssignment saved = permissionAssignmentRepository.saveAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", null, Permission.READ));

        permissionAssignmentRepository.delete(saved);
        permissionAssignmentRepository.flush();

        assertThat(permissionAssignmentRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnNameReturnsMatch() {
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", "salary", Permission.UPDATE));

        Optional<PermissionAssignment> found = permissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                        PrincipalType.USER, 7L, 1L, "public", "employees", "salary");

        assertThat(found).isPresent();
        assertThat(found.get().getPermission()).isEqualTo(Permission.UPDATE);
    }

    @Test
    void findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnNameDistinguishesNullColumn() {
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", null, Permission.READ));

        Optional<PermissionAssignment> tableLevel = permissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                        PrincipalType.USER, 7L, 1L, "public", "employees", null);
        Optional<PermissionAssignment> columnLevel = permissionAssignmentRepository
                .findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(
                        PrincipalType.USER, 7L, 1L, "public", "employees", "salary");

        assertThat(tableLevel).isPresent();
        assertThat(columnLevel).isEmpty();
    }

    @Test
    void findByConnectionIdReturnsAllAssignmentsOfConnection() {
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", null, Permission.READ));
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.GROUP, 3L, 1L, "public", "orders", null, Permission.UPDATE));
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 2L, "public", "employees", null, Permission.READ));

        List<PermissionAssignment> assignments = permissionAssignmentRepository.findByConnectionId(1L);

        assertThat(assignments).extracting(PermissionAssignment::getTableName)
                .containsExactlyInAnyOrder("employees", "orders");
    }

    @Test
    void deleteByConnectionIdRemovesAllAssignmentsOfConnection() {
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", null, Permission.READ));
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 2L, "public", "employees", null, Permission.READ));

        permissionAssignmentRepository.deleteByConnectionId(1L);
        entityManager.flush();

        assertThat(permissionAssignmentRepository.findByConnectionId(1L)).isEmpty();
        assertThat(permissionAssignmentRepository.findByConnectionId(2L)).hasSize(1);
    }

    @Test
    void deleteByPrincipalTypeAndPrincipalIdRemovesAllAssignmentsOfPrincipal() {
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.GROUP, 3L, 1L, "public", "employees", null, Permission.READ));
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.GROUP, 3L, 2L, "public", "orders", null, Permission.READ));
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 3L, 1L, "public", "employees", null, Permission.READ));

        permissionAssignmentRepository.deleteByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, 3L);
        entityManager.flush();

        assertThat(permissionAssignmentRepository.findByConnectionId(1L))
                .extracting(PermissionAssignment::getPrincipalType)
                .containsExactly(PrincipalType.USER);
        assertThat(permissionAssignmentRepository.findByConnectionId(2L)).isEmpty();
    }

    @Test
    void savingDuplicateKeyViolatesUniqueConstraint() {
        // columnNameはNULL許容カラムだが、unique制約検証はNULL同士が別値扱いされないよう
        // 非NULL値で固定した組み合わせを使う。
        entityManager.persistAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", "salary", Permission.READ));

        assertThatThrownBy(() -> permissionAssignmentRepository.saveAndFlush(
                newAssignment(PrincipalType.USER, 7L, 1L, "public", "employees", "salary", Permission.UPDATE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}