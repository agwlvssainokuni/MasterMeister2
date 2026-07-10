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

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "aux_permission_assignment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                "principalType", "principalId", "connectionId", "schemaName", "tableName", "auxType"
        })
})
public class AuxPermissionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrincipalType principalType;

    @Column(nullable = false)
    private Long principalId;

    @Column(nullable = false)
    private Long connectionId;

    @Column(nullable = false)
    private String schemaName;

    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuxPermissionType auxType;

    @Column(nullable = false)
    private boolean granted;

    private Instant updatedAt;

    protected AuxPermissionAssignment() {
    }

    public AuxPermissionAssignment(
            PrincipalType principalType,
            Long principalId,
            Long connectionId,
            String schemaName,
            String tableName,
            AuxPermissionType auxType,
            boolean granted,
            Instant updatedAt
    ) {
        this.principalType = principalType;
        this.principalId = principalId;
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.auxType = auxType;
        this.granted = granted;
        this.updatedAt = updatedAt;
    }

    public void update(
            boolean granted,
            Instant updatedAt
    ) {
        this.granted = granted;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public AuxPermissionType getAuxType() {
        return auxType;
    }

    public boolean isGranted() {
        return granted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}