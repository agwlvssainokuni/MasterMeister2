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

import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import { getQuery } from '../savedQuery/api'
import { detectParams, executeAdhocSql, executeSavedQuery } from './api'
import type { PagingOption, QueryResult } from './types'

const DEFAULT_PAGING: PagingOption = { enabled: false, page: 0, pageSize: 50 }

export function QueryExecutionPage() {
  const [searchParams] = useSearchParams()
  const connectionId = searchParams.get('connectionId') !== null ? Number(searchParams.get('connectionId')) : null
  const savedQueryId = searchParams.get('savedQueryId') !== null ? Number(searchParams.get('savedQueryId')) : null
  const readOnly = savedQueryId !== null

  const [sql, setSql] = useState(searchParams.get('rawSql') ?? '')
  const [paramValues, setParamValues] = useState<Record<string, string>>({})
  const [paging, setPaging] = useState<PagingOption>(DEFAULT_PAGING)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (savedQueryId !== null) {
      getQuery(savedQueryId).then((detail) => setSql(detail.sql))
    }
  }, [savedQueryId])

  const detectedParams = useMemo(() => detectParams(sql), [sql])

  const handleExecute = async (pagingOverride?: PagingOption) => {
    if (connectionId === null) {
      return
    }
    const effectivePaging = pagingOverride ?? paging
    try {
      const params: Record<string, unknown> = {}
      detectedParams.forEach((p) => {
        params[p.name] = paramValues[p.name] ?? ''
      })
      const queryResult =
        savedQueryId !== null
          ? await executeSavedQuery(connectionId, savedQueryId, params, effectivePaging)
          : await executeAdhocSql(connectionId, sql, params, effectivePaging)
      setResult(queryResult)
      setError(null)
    } catch (e) {
      setResult(null)
      setError(e instanceof ApiError ? e.message : 'SQL実行に失敗しました。')
    }
  }

  const handleChangePage = (nextPage: number) => {
    const nextPaging = { ...paging, page: nextPage }
    setPaging(nextPaging)
    handleExecute(nextPaging)
  }

  const dataColumns: DataTableColumn<unknown[]>[] =
    result?.columns.map((column, i) => ({
      key: column.columnName,
      header: column.columnName,
      render: (row) => <span>{row[i] == null ? '' : String(row[i])}</span>,
    })) ?? []

  return (
    <div className="query-execution-page" data-testid="query-execution-page">
      <h1>クエリ実行</h1>

      {connectionId === null ? (
        <p>接続が指定されていません。</p>
      ) : (
        <>
          <label>
            SQL
            <textarea
              data-testid="query-execution-page-sql-textarea"
              value={sql}
              readOnly={readOnly}
              onChange={(e) => setSql(e.target.value)}
            />
          </label>

          {detectedParams.length > 0 && (
            <div className="query-execution-page-params" data-testid="query-execution-page-params">
              {detectedParams.map((p) => (
                <label key={p.name}>
                  {p.name}
                  <input
                    type="text"
                    data-testid={`query-execution-page-param-${p.name}`}
                    value={paramValues[p.name] ?? ''}
                    onChange={(e) => setParamValues((prev) => ({ ...prev, [p.name]: e.target.value }))}
                  />
                </label>
              ))}
            </div>
          )}

          <label>
            <input
              type="checkbox"
              data-testid="query-execution-page-paging-checkbox"
              checked={paging.enabled}
              onChange={(e) => setPaging((prev) => ({ ...prev, enabled: e.target.checked }))}
            />
            ページングする
          </label>
          {paging.enabled && (
            <label>
              1ページの件数
              <input
                type="number"
                data-testid="query-execution-page-paging-page-size-input"
                value={paging.pageSize}
                onChange={(e) => setPaging((prev) => ({ ...prev, pageSize: Number(e.target.value) }))}
              />
            </label>
          )}

          <button type="button" data-testid="query-execution-page-execute-button" onClick={() => handleExecute()}>
            実行
          </button>

          {error !== null && (
            <p data-testid="query-execution-page-error" role="alert">
              {error}
            </p>
          )}

          {result !== null && (
            <div className="query-execution-page-result" data-testid="query-execution-page-result">
              <DataTable columns={dataColumns} rows={result.rows} getRowKey={(row) => JSON.stringify(row)} />
              <p>
                {result.totalRows}件{result.truncated ? '（上限により打ち切り）' : ''}
              </p>
              {paging.enabled && (
                <div className="query-execution-page-pagination">
                  <button
                    type="button"
                    data-testid="query-execution-page-prev-button"
                    disabled={paging.page === 0}
                    onClick={() => handleChangePage(paging.page - 1)}
                  >
                    前へ
                  </button>
                  <span>{paging.page + 1}ページ</span>
                  <button
                    type="button"
                    data-testid="query-execution-page-next-button"
                    onClick={() => handleChangePage(paging.page + 1)}
                  >
                    次へ
                  </button>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}