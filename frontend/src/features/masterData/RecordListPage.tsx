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
import { useParams } from 'react-router-dom'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import type { PageRequest } from '../../types/api'
import { applyChanges, listAccessibleTables, listRecords } from './api'
import { FilterPanel } from './FilterPanel'
import { MutationResultDialog } from './MutationResultDialog'
import type {
  ColumnMetadata,
  FilterCriteria,
  MutationResult,
  RecordCreate,
  RecordListResult,
  RecordUpdate,
  TableSummary,
} from './types'

const EMPTY_CRITERIA: FilterCriteria = {
  mode: 'UI',
  uiConditions: [],
  uiSorts: [],
  rawWhere: null,
  rawOrderBy: null,
}

const DEFAULT_PAGE: PageRequest = { page: 0, pageSize: 50 }

interface PendingChanges {
  creates: RecordCreate[]
  updates: RecordUpdate[]
  deletes: RecordDeleteEntry[]
}

interface RecordDeleteEntry {
  primaryKeyValues: Record<string, unknown>
}

interface RecordRow {
  key: string
  values: unknown[]
}

function pkKey(pk: Record<string, unknown>): string {
  return Object.keys(pk)
    .sort()
    .map((k) => `${k}=${String(pk[k])}`)
    .join('&')
}

function buildPrimaryKeyValues(columns: ColumnMetadata[], row: unknown[]): Record<string, unknown> {
  const pk: Record<string, unknown> = {}
  columns.forEach((column, i) => {
    if (column.primaryKeySequence != null) {
      pk[column.columnName] = row[i]
    }
  })
  return pk
}

function rowKeyOf(columns: ColumnMetadata[], row: unknown[]): string {
  const pk = buildPrimaryKeyValues(columns, row)
  return Object.keys(pk).length > 0 ? pkKey(pk) : JSON.stringify(row)
}

export function RecordListPage() {
  const params = useParams<{ connectionId: string; schema: string; table: string }>()
  const connectionId = Number(params.connectionId)
  const schema = params.schema ?? ''
  const table = params.table ?? ''

  const [tableSummary, setTableSummary] = useState<TableSummary | null>(null)
  const [result, setResult] = useState<RecordListResult | null>(null)
  const [criteria, setCriteria] = useState<FilterCriteria>(EMPTY_CRITERIA)
  const [page, setPage] = useState<PageRequest>(DEFAULT_PAGE)
  const [pendingChanges, setPendingChanges] = useState<PendingChanges>({ creates: [], updates: [], deletes: [] })
  const [loading, setLoading] = useState(false)
  const [mutationResult, setMutationResult] = useState<MutationResult | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    listAccessibleTables(connectionId, schema).then((tables) => {
      setTableSummary(tables.find((t) => t.tableName === table) ?? null)
    })
  }, [connectionId, schema, table])

  useEffect(() => {
    setLoading(true)
    listRecords(connectionId, schema, table, criteria, page)
      .then(setResult)
      .finally(() => setLoading(false))
  }, [connectionId, schema, table, criteria, page, reloadKey])

  const handleCriteriaChange = (nextCriteria: FilterCriteria) => {
    setCriteria(nextCriteria)
    setPage(DEFAULT_PAGE)
  }

  const handleEditCell = (rowValues: unknown[], columnName: string, value: string) => {
    if (result === null) {
      return
    }
    const primaryKeyValues = buildPrimaryKeyValues(result.columns, rowValues)
    const key = pkKey(primaryKeyValues)
    setPendingChanges((prev) => {
      const index = prev.updates.findIndex((u) => pkKey(u.primaryKeyValues) === key)
      if (index >= 0) {
        const updates = [...prev.updates]
        updates[index] = {
          ...updates[index],
          changedValues: { ...updates[index].changedValues, [columnName]: value },
        }
        return { ...prev, updates }
      }
      return { ...prev, updates: [...prev.updates, { primaryKeyValues, changedValues: { [columnName]: value } }] }
    })
  }

  const handleToggleSelect = (rowValues: unknown[]) => {
    if (result === null) {
      return
    }
    const primaryKeyValues = buildPrimaryKeyValues(result.columns, rowValues)
    const key = pkKey(primaryKeyValues)
    setPendingChanges((prev) => {
      const exists = prev.deletes.some((d) => pkKey(d.primaryKeyValues) === key)
      return {
        ...prev,
        deletes: exists
          ? prev.deletes.filter((d) => pkKey(d.primaryKeyValues) !== key)
          : [...prev.deletes, { primaryKeyValues }],
      }
    })
  }

  const handleAddRow = () => {
    setPendingChanges((prev) => ({ ...prev, creates: [...prev.creates, { values: {} }] }))
  }

  const handleEditNewRow = (index: number, columnName: string, value: string) => {
    setPendingChanges((prev) => {
      const creates = [...prev.creates]
      creates[index] = { values: { ...creates[index].values, [columnName]: value } }
      return { ...prev, creates }
    })
  }

  const handleRemoveNewRow = (index: number) => {
    setPendingChanges((prev) => ({ ...prev, creates: prev.creates.filter((_, i) => i !== index) }))
  }

  const handleApply = () => {
    applyChanges(connectionId, schema, table, {
      creates: pendingChanges.creates,
      updates: pendingChanges.updates,
      deletes: pendingChanges.deletes,
    }).then((mutation) => {
      setMutationResult(mutation)
      if (mutation.success) {
        setPendingChanges({ creates: [], updates: [], deletes: [] })
        setReloadKey((k) => k + 1)
      }
    })
  }

  const columns = result?.columns ?? []

  const rows: RecordRow[] = (result?.records.content ?? []).map((values) => ({
    key: rowKeyOf(columns, values),
    values,
  }))

  const dataColumns: DataTableColumn<RecordRow>[] = columns.map((column, i) => ({
    key: column.columnName,
    header: column.columnName,
    render: (row) => {
      if (column.effectivePermission !== 'UPDATE') {
        return <span>{row.values[i] == null ? '' : String(row.values[i])}</span>
      }
      const primaryKeyValues = buildPrimaryKeyValues(columns, row.values)
      const pending = pendingChanges.updates.find((u) => pkKey(u.primaryKeyValues) === pkKey(primaryKeyValues))
      const edited = pending?.changedValues[column.columnName]
      const value = edited !== undefined ? edited : row.values[i]
      return (
        <input
          type="text"
          value={value == null ? '' : String(value)}
          onChange={(e) => handleEditCell(row.values, column.columnName, e.target.value)}
        />
      )
    },
  }))

  const tableColumns: DataTableColumn<RecordRow>[] = tableSummary?.canDelete
    ? [
        {
          key: 'select',
          header: '削除',
          render: (row) => (
            <input
              type="checkbox"
              data-testid="record-list-page-delete-checkbox"
              checked={pendingChanges.deletes.some(
                (d) => pkKey(d.primaryKeyValues) === pkKey(buildPrimaryKeyValues(columns, row.values)),
              )}
              onChange={() => handleToggleSelect(row.values)}
            />
          ),
        },
        ...dataColumns,
      ]
    : dataColumns

  return (
    <div className="record-list-page" data-testid="record-list-page">
      <h1>
        {schema}.{table}
      </h1>

      <FilterPanel columns={columns} criteria={criteria} onChange={handleCriteriaChange} />

      {loading ? (
        <p>読み込み中...</p>
      ) : (
        <>
          <DataTable columns={tableColumns} rows={rows} getRowKey={(row) => row.key} />

          <div className="record-list-page-pagination">
            <button
              type="button"
              disabled={page.page === 0}
              onClick={() => setPage((prev) => ({ ...prev, page: prev.page - 1 }))}
            >
              前へ
            </button>
            <span>
              {page.page + 1}ページ / 全{result?.records.totalCount ?? 0}件
            </span>
            <button
              type="button"
              disabled={result === null || (page.page + 1) * page.pageSize >= result.records.totalCount}
              onClick={() => setPage((prev) => ({ ...prev, page: prev.page + 1 }))}
            >
              次へ
            </button>
          </div>

          {tableSummary?.canCreate && (
            <div className="record-list-page-new-rows" data-testid="record-list-page-new-rows">
              <h2>新規行</h2>
              <button type="button" data-testid="record-list-page-add-row" onClick={handleAddRow}>
                新規行を追加
              </button>
              <table>
                <thead>
                  <tr>
                    {columns.map((column) => (
                      <th key={column.columnName}>{column.columnName}</th>
                    ))}
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingChanges.creates.map((create, index) => (
                    <tr key={index}>
                      {columns.map((column) => (
                        <td key={column.columnName}>
                          <input
                            type="text"
                            value={
                              create.values[column.columnName] == null
                                ? ''
                                : String(create.values[column.columnName])
                            }
                            onChange={(e) => handleEditNewRow(index, column.columnName, e.target.value)}
                          />
                        </td>
                      ))}
                      <td>
                        <button type="button" onClick={() => handleRemoveNewRow(index)}>
                          削除
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <button type="button" className="btn-primary" data-testid="record-list-page-apply" onClick={handleApply}>
            反映
          </button>
        </>
      )}

      <MutationResultDialog result={mutationResult} onClose={() => setMutationResult(null)} />
    </div>
  )
}