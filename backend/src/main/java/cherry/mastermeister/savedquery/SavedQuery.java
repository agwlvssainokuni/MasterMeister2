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

package cherry.mastermeister.savedquery;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "saved_query")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long connectionId;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(nullable = false)
    private String sql;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Column(nullable = false)
    private boolean retired;

    @Column(nullable = false)
    private int executionCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SavedQuery() {
    }

    public SavedQuery(
            Long ownerId,
            Long connectionId,
            String name,
            String sql,
            Visibility visibility,
            boolean retired,
            int executionCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.ownerId = ownerId;
        this.connectionId = connectionId;
        this.name = name;
        this.sql = sql;
        this.visibility = visibility;
        this.retired = retired;
        this.executionCount = executionCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String name, String sql, Visibility visibility, Instant updatedAt) {
        this.name = name;
        this.sql = sql;
        this.visibility = visibility;
        this.updatedAt = updatedAt;
    }

    public void retire(Instant updatedAt) {
        this.retired = true;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getName() {
        return name;
    }

    public String getSql() {
        return sql;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public boolean isRetired() {
        return retired;
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}