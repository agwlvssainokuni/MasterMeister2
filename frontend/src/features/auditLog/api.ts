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