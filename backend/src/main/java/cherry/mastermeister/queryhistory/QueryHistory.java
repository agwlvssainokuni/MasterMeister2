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

package cherry.mastermeister.queryhistory;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 実行のたびのスナップショット（{@code sql}/{@code params}/{@code savedQueryName}）。
 * 参照元の{@code SavedQuery}が後から編集・廃止されても内容は変化しない
 * （{@code business-rules.md} 3節）。{@code (connectionId, executedAt)}・{@code savedQueryId}
 * に明示的インデックスを追加する（{@code nfr-design-patterns.md} 2、リテンションなしで
 * 無制限に増加するため）。
 */
@Entity
@Table(
        name = "query_history",
        indexes = {
                @Index(name = "idx_query_history_connection_executed", columnList = "connectionId, executedAt"),
                @Index(name = "idx_query_history_saved_query", columnList = "savedQueryId")
        }
)
public class QueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long connectionId;

    @Column(nullable = false)
    private String schema;

    @Lob
    @Column(nullable = false)
    private String sql;

    @Convert(converter = JsonMapConverter.class)
    @Lob
    private Map<String, Object> params;

    @Column(nullable = false)
    private int resultCount;

    @Column(nullable = false)
    private long elapsedMillis;

    @Column(nullable = false)
    private Instant executedAt;

    private Long savedQueryId;

    private String savedQueryName;

    private Integer executionCount;

    protected QueryHistory() {
    }

    public QueryHistory(
            Long userId,
            Long connectionId,
            String schema,
            String sql,
            Map<String, Object> params,
            int resultCount,
            long elapsedMillis,
            Instant executedAt,
            Long savedQueryId,
            String savedQueryName,
            Integer executionCount
    ) {
        this.userId = userId;
        this.connectionId = connectionId;
        this.schema = schema;
        this.sql = sql;
        this.params = params;
        this.resultCount = resultCount;
        this.elapsedMillis = elapsedMillis;
        this.executedAt = executedAt;
        this.savedQueryId = savedQueryId;
        this.savedQueryName = savedQueryName;
        this.executionCount = executionCount;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public String getSchema() {
        return schema;
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public int getResultCount() {
        return resultCount;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Long getSavedQueryId() {
        return savedQueryId;
    }

    public String getSavedQueryName() {
        return savedQueryName;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

}