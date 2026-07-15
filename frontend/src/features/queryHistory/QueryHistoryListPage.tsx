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
import { useNavigate } from 'react-router-dom'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import { Pagination } from '../../components/Pagination'
import { useConnection } from '../../hooks/useConnection'
import { usePagination } from '../../hooks/usePagination'
import { listHistory } from './api'
import type { ExecutorScope, HistoryEntry, HistoryFilterCriteria } from './types'

// mm.app.query-history.default-page-size / page-size-options (application.yml) と揃える
const DEFAULT_PAGE_SIZE = 50
const PAGE_SIZE_OPTIONS = [50, 100, 200]

const EMPTY_CRITERIA: HistoryFilterCriteria = { executorScope: 'ALL' }

export function QueryHistoryListPage() {
  const navigate = useNavigate()
  const { connectionId } = useConnection()

  const [criteria, setCriteria] = useState<HistoryFilterCriteria>(EMPTY_CRITERIA)
  const [executedAtFrom, setExecutedAtFrom] = useState('')
  const [executedAtTo, setExecutedAtTo] = useState('')
  const [sqlTextSearch, setSqlTextSearch] = useState('')
  const [rows, setRows] = useState<HistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const { page, pageSize, totalCount, pageRequest, setTotalCount, goToPage, changePageSize } =
    usePagination(DEFAULT_PAGE_SIZE)

  const runSearch = useCallback(async () => {
    if (connectionId === null) {
      return
    }
    setLoading(true)
    try {
      const result = await listHistory(connectionId, criteria, pageRequest)
      setRows(result.content)
      setTotalCount(result.totalCount)
    } finally {
      setLoading(false)
    }
  }, [connectionId, criteria, pageRequest, setTotalCount])

  useEffect(() => {
    runSearch()
  }, [runSearch])

  const handleSearch = () => {
    setCriteria({
      executedAtFrom: executedAtFrom || undefined,
      executedAtTo: executedAtTo || undefined,
      executorScope: criteria.executorScope,
      sqlTextSearch: sqlTextSearch || undefined,
    })
    goToPage(0)
  }

  const handleScopeChange = (scope: ExecutorScope) => {
    setCriteria((prev) => ({ ...prev, executorScope: scope }))
    goToPage(0)
  }

  const navigateWithSql = (path: string, entry: HistoryEntry, includeSchema: boolean) => {
    const params = new URLSearchParams()
    params.set('rawSql', entry.sql)
    if (includeSchema) {
      params.set('schema', entry.schema)
    }
    navigate(`${path}?${params.toString()}`)
  }

  const columns: DataTableColumn<HistoryEntry>[] = [
    {
      key: 'sql',
      header: 'SQL',
      render: (row) => <pre>{row.sql}</pre>,
    },
    { key: 'schema', header: 'スキーマ' },
    {
      key: 'source',
      header: '種別',
      render: (row) => (row.savedQueryId !== null ? `保存クエリ: ${row.savedQueryName ?? ''}` : '直接入力'),
    },
    { key: 'resultCount', header: '結果件数' },
    { key: 'elapsedMillis', header: '実行時間(ms)' },
    {
      key: 'executedAt',
      header: '実行日時',
      render: (row) => new Date(row.executedAt).toLocaleString(),
    },
    {
      key: 'retired',
      header: '状態',
      render: (row) => (row.retired ? '廃止済み' : ''),
    },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <>
          <button
            type="button"
            data-testid="query-history-list-page-rerun-button"
            onClick={() => navigateWithSql('/query-execution', row, true)}
          >
            再実行
          </button>
          <button
            type="button"
            data-testid="query-history-list-page-save-button"
            onClick={() => navigateWithSql('/saved-queries/new', row, false)}
          >
            保存
          </button>
          <button
            type="button"
            data-testid="query-history-list-page-edit-in-builder-button"
            onClick={() => navigateWithSql('/query-builder', row, true)}
          >
            ビルダーで編集
          </button>
        </>
      ),
    },
  ]

  return (
    <div className="query-history-list-page" data-testid="query-history-list-page">
      <h1>クエリ履歴</h1>

      {connectionId === null ? (
        <p>接続が指定されていません。</p>
      ) : (
        <>
          <div className="query-history-list-page-filter">
            <label>
              実行日時（開始）
              <input
                type="datetime-local"
                data-testid="query-history-list-page-executed-at-from-input"
                value={executedAtFrom}
                onChange={(e) => setExecutedAtFrom(e.target.value)}
              />
            </label>
            <label>
              実行日時（終了）
              <input
                type="datetime-local"
                data-testid="query-history-list-page-executed-at-to-input"
                value={executedAtTo}
                onChange={(e) => setExecutedAtTo(e.target.value)}
              />
            </label>
            <label>
              実行者
              <select
                data-testid="query-history-list-page-executor-scope-select"
                value={criteria.executorScope}
                onChange={(e) => handleScopeChange(e.target.value as ExecutorScope)}
              >
                <option value="ALL">全ユーザ</option>
                <option value="SELF">自分のみ</option>
              </select>
            </label>
            <label>
              SQLテキスト検索
              <input
                type="text"
                data-testid="query-history-list-page-sql-text-search-input"
                value={sqlTextSearch}
                onChange={(e) => setSqlTextSearch(e.target.value)}
              />
            </label>
            <button type="button" data-testid="query-history-list-page-search-button" onClick={handleSearch}>
              検索
            </button>
          </div>

          {loading ? (
            <p>読み込み中...</p>
          ) : (
            <DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} />
          )}

          <Pagination
            page={page}
            pageSize={pageSize}
            pageSizeOptions={PAGE_SIZE_OPTIONS}
            totalCount={totalCount}
            onPageChange={goToPage}
            onPageSizeChange={changePageSize}
          />
        </>
      )}
    </div>
  )
}