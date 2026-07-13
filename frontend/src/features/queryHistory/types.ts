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

export type ExecutorScope = 'ALL' | 'SELF'

export interface HistoryEntry {
  id: number
  userId: number
  connectionId: number
  sql: string
  params: Record<string, unknown>
  resultCount: number
  elapsedMillis: number
  executedAt: string
  savedQueryId: number | null
  savedQueryName: string | null
  executionCount: number | null
  retired: boolean
  masked: boolean
}

export interface HistoryFilterCriteria {
  executedAtFrom?: string
  executedAtTo?: string
  executorScope: ExecutorScope
  sqlTextSearch?: string
}