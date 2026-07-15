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
import type { PageRequest } from '../../types/api'
import type { FilterCriteria, MutationRequest, MutationResult, RecordListResult, TableSummary } from './types'

export function listAccessibleSchemas(connectionId: number): Promise<string[]> {
  return apiFetch<string[]>(`/api/master-data/${connectionId}/schemas`)
}

export function listAccessibleTables(connectionId: number, schema: string): Promise<TableSummary[]> {
  return apiFetch<TableSummary[]>(
    `/api/master-data/${connectionId}/schemas/${encodeURIComponent(schema)}/tables`,
  )
}

export function listRecords(
  connectionId: number,
  schema: string,
  table: string,
  criteria: FilterCriteria,
  page: PageRequest,
): Promise<RecordListResult> {
  return apiFetch<RecordListResult>(
    `/api/master-data/${connectionId}/schemas/${encodeURIComponent(schema)}/tables/`
      + `${encodeURIComponent(table)}/records:search`,
    { method: 'POST', body: { criteria, page: page.page, pageSize: page.pageSize } },
  )
}

export function applyChanges(
  connectionId: number,
  schema: string,
  table: string,
  request: MutationRequest,
): Promise<MutationResult> {
  return apiFetch<MutationResult>(
    `/api/master-data/${connectionId}/schemas/${encodeURIComponent(schema)}/tables/`
      + `${encodeURIComponent(table)}/records:apply`,
    { method: 'POST', body: request },
  )
}