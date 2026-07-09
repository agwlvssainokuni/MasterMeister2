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

import { useCallback, useEffect, useState } from 'react'
import { Pagination } from '../../components/Pagination'
import { usePagination } from '../../hooks/usePagination'
import { searchAuditLogs } from './api'
import { AuditLogFilterPanel } from './AuditLogFilterPanel'
import { AuditLogTable } from './AuditLogTable'
import type { AuditLog, AuditLogFilter } from './types'

// mm.app.audit.default-page-size / page-size-options (application.yml) と揃える
const DEFAULT_PAGE_SIZE = 20
const PAGE_SIZE_OPTIONS = [20, 50, 100]

export function AuditLogPage() {
  const [filter, setFilter] = useState<AuditLogFilter>({})
  const [rows, setRows] = useState<AuditLog[]>([])
  const [loading, setLoading] = useState(false)
  const { page, pageSize, totalCount, pageRequest, setTotalCount, goToPage, changePageSize } =
    usePagination(DEFAULT_PAGE_SIZE)

  const runSearch = useCallback(async () => {
    setLoading(true)
    try {
      const result = await searchAuditLogs(filter, pageRequest)
      setRows(result.content)
      setTotalCount(result.totalCount)
    } finally {
      setLoading(false)
    }
  }, [filter, pageRequest, setTotalCount])

  useEffect(() => {
    runSearch()
  }, [runSearch])

  return (
    <div className="audit-log-page" data-testid="audit-log-page">
      <h1>監査ログ</h1>
      <AuditLogFilterPanel filter={filter} onFilterChange={setFilter} />
      <AuditLogTable rows={rows} loading={loading} />
      <Pagination
        page={page}
        pageSize={pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        totalCount={totalCount}
        onPageChange={goToPage}
        onPageSizeChange={changePageSize}
      />
    </div>
  )
}