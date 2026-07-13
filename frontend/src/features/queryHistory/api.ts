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
import type { PageRequest, PageResult } from '../../types/api'
import type { HistoryEntry, HistoryFilterCriteria } from './types'

export function listHistory(
  connectionId: number,
  criteria: HistoryFilterCriteria,
  page: PageRequest,
): Promise<PageResult<HistoryEntry>> {
  const params = new URLSearchParams()
  params.set('connectionId', String(connectionId))
  if (criteria.executedAtFrom) params.set('executedAtFrom', criteria.executedAtFrom)
  if (criteria.executedAtTo) params.set('executedAtTo', criteria.executedAtTo)
  params.set('executorScope', criteria.executorScope)
  if (criteria.sqlTextSearch) params.set('sqlTextSearch', criteria.sqlTextSearch)
  params.set('page', String(page.page))
  params.set('pageSize', String(page.pageSize))

  return apiFetch<PageResult<HistoryEntry>>(`/api/query-history?${params.toString()}`)
}