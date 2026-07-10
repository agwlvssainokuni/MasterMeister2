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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "schema_column", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tableId", "columnName"})
})
public class SchemaColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tableId;

    @Column(nullable = false)
    private String columnName;

    @Column(nullable = false)
    private String dataType;

    @Column(nullable = false)
    private boolean nullable;

    private String comment;

    @Column(nullable = false)
    private Integer ordinalPosition;

    private Integer primaryKeySequence;

    @Column(nullable = false)
    private boolean stale;

    private Instant importedAt;

    private Instant updatedAt;

    protected SchemaColumn() {
    }

    public SchemaColumn(
            Long tableId,
            String columnName,
            String dataType,
            boolean nullable,
            String comment,
            Integer ordinalPosition,
            Integer primaryKeySequence,
            boolean stale,
            Instant importedAt,
            Instant updatedAt
    ) {
        this.tableId = tableId;
        this.columnName = columnName;
        this.dataType = dataType;
        this.nullable = nullable;
        this.comment = comment;
        this.ordinalPosition = ordinalPosition;
        this.primaryKeySequence = primaryKeySequence;
        this.stale = stale;
        this.importedAt = importedAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String dataType,
            boolean nullable,
            String comment,
            Integer ordinalPosition,
            Integer primaryKeySequence,
            boolean stale,
            Instant updatedAt
    ) {
        this.dataType = dataType;
        this.nullable = nullable;
        this.comment = comment;
        this.ordinalPosition = ordinalPosition;
        this.primaryKeySequence = primaryKeySequence;
        this.stale = stale;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTableId() {
        return tableId;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getComment() {
        return comment;
    }

    public Integer getOrdinalPosition() {
        return ordinalPosition;
    }

    public Integer getPrimaryKeySequence() {
        return primaryKeySequence;
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