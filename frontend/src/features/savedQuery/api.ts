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
import type { SavedQueryDetail, SavedQuerySummary, Visibility } from './types'

export function listQueries(connectionId: number, includeRetired: boolean): Promise<SavedQuerySummary[]> {
  const params = new URLSearchParams()
  params.set('connectionId', String(connectionId))
  params.set('includeRetired', String(includeRetired))
  return apiFetch<SavedQuerySummary[]>(`/api/saved-queries?${params.toString()}`)
}

export function saveQuery(
  connectionId: number,
  name: string,
  sql: string,
  visibility: Visibility,
): Promise<number> {
  return apiFetch<number>('/api/saved-queries', {
    method: 'POST',
    body: { connectionId, name, sql, visibility },
  })
}

export function getQuery(savedQueryId: number): Promise<SavedQueryDetail> {
  return apiFetch<SavedQueryDetail>(`/api/saved-queries/${savedQueryId}`)
}

export function updateQuery(
  savedQueryId: number,
  name: string,
  sql: string,
  visibility: Visibility,
): Promise<void> {
  return apiFetch<void>(`/api/saved-queries/${savedQueryId}`, {
    method: 'PUT',
    body: { name, sql, visibility },
  })
}

export function retireQuery(savedQueryId: number): Promise<void> {
  return apiFetch<void>(`/api/saved-queries/${savedQueryId}/retire`, { method: 'POST' })
}