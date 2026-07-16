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

import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import { useConnection } from '../../hooks/useConnection'
import { getQuery } from '../savedQuery/api'
import { detectParams, executeAdhocSql, executeSavedQuery, listAccessibleSchemas } from './api'
import type { PagingOption, QueryResult } from './types'

const DEFAULT_PAGING: PagingOption = { enabled: false, page: 0, pageSize: 50 }

export function QueryExecutionPage() {
  const [searchParams] = useSearchParams()
  const savedQueryId = searchParams.get('savedQueryId') !== null ? Number(searchParams.get('savedQueryId')) : null
  const readOnly = savedQueryId !== null
  const { connectionId: globalConnectionId } = useConnection()

  const [savedQueryConnectionId, setSavedQueryConnectionId] = useState<number | null>(null)
  // savedQueryId指定時はSavedQueryDetail.connectionId（保存クエリに固定された値）を、
  // 未指定時はグローバル接続コンテキストを用いる（frontend-components.md、2026-07-15変更要求・訂正）
  const connectionId = savedQueryId !== null ? savedQueryConnectionId : globalConnectionId

  const [sql, setSql] = useState(searchParams.get('rawSql') ?? '')
  const [schemas, setSchemas] = useState<string[]>([])
  const [schema, setSchema] = useState<string | null>(null)
  const [paramValues, setParamValues] = useState<Record<string, string>>({})
  const [paging, setPaging] = useState<PagingOption>(DEFAULT_PAGING)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const initializedConnectionId = useRef<number | null | undefined>(undefined)

  useEffect(() => {
    if (savedQueryId !== null) {
      getQuery(savedQueryId).then((detail) => {
        setSql(detail.sql)
        setSavedQueryConnectionId(detail.connectionId)
      })
    }
  }, [savedQueryId])

  useEffect(() => {
    if (connectionId === null) {
      setSchemas([])
      return
    }
    listAccessibleSchemas(connectionId).then(setSchemas)
    // StrictModeの開発時二重effect実行では同一connectionIdで再度呼ばれるため、
    // 「既にこのconnectionIdで初期化済みか」で判定する（真の接続切替と区別する）
    if (initializedConnectionId.current === connectionId) {
      return
    }
    const isFirstInit = initializedConnectionId.current === undefined
    initializedConnectionId.current = connectionId
    if (isFirstInit) {
      const urlSchema = searchParams.get('schema')
      setSchema(urlSchema)
      return
    }
    setSchema(null)
    // 接続切替時のスキーマ選択リセット（QueryBuilderPageと同じ流儀）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionId])

  const detectedParams = useMemo(() => detectParams(sql), [sql])

  const handleExecute = async (pagingOverride?: PagingOption) => {
    if (connectionId === null || schema === null) {
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
          ? await executeSavedQuery(connectionId, schema, savedQueryId, params, effectivePaging)
          : await executeAdhocSql(connectionId, schema, sql, params, effectivePaging)
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
            スキーマ
            <select
              data-testid="query-execution-page-schema-select"
              value={schema ?? ''}
              onChange={(e) => setSchema(e.target.value)}
            >
              <option value="" disabled>
                選択してください
              </option>
              {schemas.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>

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

          <button
            type="button"
            data-testid="query-execution-page-execute-button"
            disabled={schema === null}
            onClick={() => handleExecute()}
          >
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