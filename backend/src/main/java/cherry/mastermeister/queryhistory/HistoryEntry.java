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

/**
 * {@code masked}が{@code true}の行は{@code sql}/{@code savedQueryName}/{@code params}が
 * プレースホルダに差し替えられている（{@code business-rules.md} 5.2-5.3）。{@code retired}は
 * マスキングとは独立に「廃止済み」バッジ表示のためのフラグ（同5.3）。
 */
public record HistoryEntry(
        Long id,
        Long userId,
        Long connectionId,
        String sql,
        Map<String, Object> params,
        int resultCount,
        long elapsedMillis,
        Instant executedAt,
        Long savedQueryId,
        String savedQueryName,
        Integer executionCount,
        boolean retired,
        boolean masked
) {
}