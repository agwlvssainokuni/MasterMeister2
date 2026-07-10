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

package cherry.mastermeister.schema;

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
@Table(name = "schema_table", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"connectionId", "schemaName", "tableName"})
})
public class SchemaTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long connectionId;

    @Column(nullable = false)
    private String schemaName;

    @Column(nullable = false)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TableType tableType;

    private String comment;

    @Column(nullable = false)
    private boolean stale;

    private Instant importedAt;

    private Instant updatedAt;

    protected SchemaTable() {
    }

    public SchemaTable(
            Long connectionId,
            String schemaName,
            String tableName,
            TableType tableType,
            String comment,
            boolean stale,
            Instant importedAt,
            Instant updatedAt
    ) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableType = tableType;
        this.comment = comment;
        this.stale = stale;
        this.importedAt = importedAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            TableType tableType,
            String comment,
            boolean stale,
            Instant updatedAt
    ) {
        this.tableType = tableType;
        this.comment = comment;
        this.stale = stale;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
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

    public TableType getTableType() {
        return tableType;
    }

    public String getComment() {
        return comment;
    }

    public boolean isStale() {
        return stale;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}