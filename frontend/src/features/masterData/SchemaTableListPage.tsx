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
import { useNavigate } from 'react-router-dom'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import { listAccessibleConnections, listAccessibleSchemas, listAccessibleTables } from './api'
import type { ConnectionSummary, TableSummary } from './types'

export function SchemaTableListPage() {
  const navigate = useNavigate()

  const [connections, setConnections] = useState<ConnectionSummary[]>([])
  const [connectionId, setConnectionId] = useState<number | null>(null)
  const [schemas, setSchemas] = useState<string[]>([])
  const [schema, setSchema] = useState<string | null>(null)
  const [tables, setTables] = useState<TableSummary[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    listAccessibleConnections().then(setConnections)
  }, [])

  const handleSelectConnection = async (selectedConnectionId: number) => {
    setConnectionId(selectedConnectionId)
    setSchema(null)
    setTables([])
    setLoading(true)
    try {
      setSchemas(await listAccessibleSchemas(selectedConnectionId))
    } finally {
      setLoading(false)
    }
  }

  const handleSelectSchema = async (selectedSchema: string) => {
    if (connectionId === null) {
      return
    }
    setSchema(selectedSchema)
    setLoading(true)
    try {
      setTables(await listAccessibleTables(connectionId, selectedSchema))
    } finally {
      setLoading(false)
    }
  }

  const handleSelectTable = (table: TableSummary) => {
    if (connectionId === null || schema === null) {
      return
    }
    navigate(`/master-data/${connectionId}/${schema}/${table.tableName}`)
  }

  const columns: DataTableColumn<TableSummary>[] = [
    { key: 'tableName', header: 'テーブル名' },
    { key: 'tableType', header: '種別' },
    { key: 'comment', header: 'コメント' },
    { key: 'effectivePermission', header: '実効権限' },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <button type="button" data-testid="schema-table-list-page-row" onClick={() => handleSelectTable(row)}>
          {row.tableName}を開く
        </button>
      ),
    },
  ]

  return (
    <div className="schema-table-list-page" data-testid="schema-table-list-page">
      <h1>マスタデータ</h1>
      <label>
        対象接続
        <select
          data-testid="schema-table-list-page-connection-select"
          value={connectionId ?? ''}
          onChange={(e) => handleSelectConnection(Number(e.target.value))}
        >
          <option value="" disabled>
            選択してください
          </option>
          {connections.map((connection) => (
            <option key={connection.id} value={connection.id}>
              {connection.name}
            </option>
          ))}
        </select>
      </label>
      {connectionId !== null && (
        <label>
          スキーマ
          <select
            data-testid="schema-table-list-page-schema-select"
            value={schema ?? ''}
            onChange={(e) => handleSelectSchema(e.target.value)}
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
      )}
      {loading ? (
        <p>読み込み中...</p>
      ) : (
        schema !== null && (
          <DataTable columns={columns} rows={tables} getRowKey={(row) => `${row.schemaName}.${row.tableName}`} />
        )
      )}
    </div>
  )
}