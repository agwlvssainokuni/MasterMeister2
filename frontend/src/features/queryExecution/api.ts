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

import { apiFetch } from '../../api/apiClient'
import type { DetectedParam, PagingOption, QueryResult } from './types'

// バックエンドのSqlParamDetector（queryexecution/SqlParamDetector.java）と同じアルゴリズムを
// フロントエンド側で再実装したもの（API呼び出しなしでUI表示用に検出するため）。文字列リテラル
// （''によるエスケープ含む）内の:xxxや、PostgreSQLの::キャスト演算子は誤検知しない。
export function detectParams(sql: string): DetectedParam[] {
  const params: DetectedParam[] = []
  const seen = new Set<string>()
  const len = sql.length
  let i = 0
  while (i < len) {
    const c = sql.charAt(i)
    if (c === "'") {
      i = skipStringLiteral(sql, i, len)
      continue
    }
    if (c === ':') {
      if (i + 1 < len && sql.charAt(i + 1) === ':') {
        i += 2
        continue
      }
      const start = i + 1
      let j = start
      while (j < len && /[A-Za-z0-9_]/.test(sql.charAt(j))) {
        j++
      }
      if (j > start) {
        const name = sql.substring(start, j)
        if (!seen.has(name)) {
          seen.add(name)
          params.push({ name })
        }
        i = j
        continue
      }
    }
    i++
  }
  return params
}

function skipStringLiteral(sql: string, start: number, len: number): number {
  let i = start + 1
  while (i < len) {
    if (sql.charAt(i) === "'") {
      if (i + 1 < len && sql.charAt(i + 1) === "'") {
        i += 2
        continue
      }
      return i + 1
    }
    i++
  }
  return i
}

export function executeAdhocSql(
  connectionId: number,
  sql: string,
  params: Record<string, unknown>,
  paging: PagingOption,
): Promise<QueryResult> {
  return apiFetch<QueryResult>('/api/query-execution/adhoc', {
    method: 'POST',
    body: { connectionId, sql, params, paging },
  })
}

export function executeSavedQuery(
  connectionId: number,
  savedQueryId: number,
  params: Record<string, unknown>,
  paging: PagingOption,
): Promise<QueryResult> {
  return apiFetch<QueryResult>(`/api/query-execution/saved/${savedQueryId}`, {
    method: 'POST',
    body: { connectionId, params, paging },
  })
}