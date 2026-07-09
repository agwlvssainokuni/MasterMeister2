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
import type { AuditLog, AuditLogFilter } from './types'

export function searchAuditLogs(filter: AuditLogFilter, pageRequest: PageRequest): Promise<PageResult<AuditLog>> {
  const params = new URLSearchParams()
  if (filter.dateFrom) params.set('dateFrom', filter.dateFrom)
  if (filter.dateTo) params.set('dateTo', filter.dateTo)
  if (filter.userId !== undefined) params.set('userId', String(filter.userId))
  if (filter.eventCategory) params.set('eventCategory', filter.eventCategory)
  if (filter.eventType) params.set('eventType', filter.eventType)
  params.set('page', String(pageRequest.page))
  params.set('pageSize', String(pageRequest.pageSize))

  return apiFetch<PageResult<AuditLog>>(`/api/audit-logs?${params.toString()}`)
}