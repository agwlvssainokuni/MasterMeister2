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

import { Link } from 'react-router-dom'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import type { ConnectionSummary } from './types'

interface ConnectionTableProps {
  connections: ConnectionSummary[]
  onTest: (connectionId: number) => void
  onImportSchema: (connectionId: number) => void
}

export function ConnectionTable({ connections, onTest, onImportSchema }: ConnectionTableProps) {
  const columns: DataTableColumn<ConnectionSummary>[] = [
    { key: 'name', header: '接続名' },
    { key: 'rdbmsType', header: '種別' },
    { key: 'host', header: 'ホスト' },
    { key: 'databaseName', header: 'データベース名' },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <>
          <Link to={`/admin/rdbms-connections/${row.id}`} data-testid="connection-table-edit-button">
            編集
          </Link>
          <button type="button" data-testid="connection-table-test-button" onClick={() => onTest(row.id)}>
            接続テスト
          </button>
          <button
            type="button"
            data-testid="connection-table-import-schema-button"
            onClick={() => onImportSchema(row.id)}
          >
            スキーマ取り込み
          </button>
        </>
      ),
    },
  ]

  return <DataTable columns={columns} rows={connections} getRowKey={(row) => row.id} />
}