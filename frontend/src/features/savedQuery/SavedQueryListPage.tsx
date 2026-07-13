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

import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import { listQueries } from './api'
import type { SavedQuerySummary } from './types'

export function SavedQueryListPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const connectionId = searchParams.get('connectionId') !== null ? Number(searchParams.get('connectionId')) : null

  const [savedQueries, setSavedQueries] = useState<SavedQuerySummary[]>([])
  const [includeRetired, setIncludeRetired] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (connectionId === null) {
      return
    }
    setLoading(true)
    listQueries(connectionId, includeRetired)
      .then(setSavedQueries)
      .finally(() => setLoading(false))
  }, [connectionId, includeRetired])

  const columns: DataTableColumn<SavedQuerySummary>[] = [
    { key: 'name', header: '名前' },
    { key: 'visibility', header: '公開範囲' },
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
            data-testid="saved-query-list-page-execute-button"
            onClick={() => navigate(`/query-execution?connectionId=${connectionId}&savedQueryId=${row.id}`)}
          >
            実行
          </button>
          <button
            type="button"
            data-testid="saved-query-list-page-detail-button"
            onClick={() => navigate(`/saved-queries/${row.id}`)}
          >
            詳細
          </button>
        </>
      ),
    },
  ]

  return (
    <div className="saved-query-list-page" data-testid="saved-query-list-page">
      <h1>保存クエリ</h1>

      {connectionId === null ? (
        <p>接続が指定されていません。</p>
      ) : (
        <>
          <label>
            <input
              type="checkbox"
              data-testid="saved-query-list-page-include-retired-checkbox"
              checked={includeRetired}
              onChange={(e) => setIncludeRetired(e.target.checked)}
            />
            廃止済みも表示
          </label>

          {loading ? (
            <p>読み込み中...</p>
          ) : (
            <DataTable columns={columns} rows={savedQueries} getRowKey={(row) => row.id} />
          )}
        </>
      )}
    </div>
  )
}