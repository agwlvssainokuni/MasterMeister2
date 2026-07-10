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
@Table(name = "permission_assignment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                "principalType", "principalId", "connectionId", "schemaName", "tableName", "columnName"
        })
})
public class PermissionAssignment {

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

    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    private Instant updatedAt;

    protected PermissionAssignment() {
    }

    public PermissionAssignment(
            PrincipalType principalType,
            Long principalId,
            Long connectionId,
            String schemaName,
            String tableName,
            String columnName,
            Permission permission,
            Instant updatedAt
    ) {
        this.principalType = principalType;
        this.principalId = principalId;
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.columnName = columnName;
        this.permission = permission;
        this.updatedAt = updatedAt;
    }

    public void update(
            Permission permission,
            Instant updatedAt
    ) {
        this.permission = permission;
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

    public String getColumnName() {
        return columnName;
    }

    public Permission getPermission() {
        return permission;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}