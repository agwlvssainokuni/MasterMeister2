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

import type { TableDetail } from './types'

interface TableDetailPanelProps {
  detail: TableDetail | null
}

export function TableDetailPanel({ detail }: TableDetailPanelProps) {
  if (!detail) {
    return null
  }

  // ビューは主キーを持たないため「なし」と表示する（business-rules.md 2.1）。
  const primaryKeyColumns = detail.columns
    .filter((column) => column.primaryKeySequence !== null)
    .sort((a, b) => (a.primaryKeySequence ?? 0) - (b.primaryKeySequence ?? 0))

  return (
    <div className="table-detail-panel" data-testid="table-detail-panel">
      <h2>{detail.tableName}</h2>
      {detail.comment && <p>{detail.comment}</p>}
      <table>
        <thead>
          <tr>
            <th>列名</th>
            <th>型</th>
            <th>NULL許容</th>
            <th>コメント</th>
          </tr>
        </thead>
        <tbody>
          {detail.columns.map((column) => (
            <tr key={column.columnName} data-testid="table-detail-panel-column-row">
              <td>{column.columnName}</td>
              <td>{column.dataType}</td>
              <td>{column.nullable ? '許容' : '不可'}</td>
              <td>{column.comment}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p data-testid="table-detail-panel-primary-key">
        主キー:{' '}
        {detail.tableType === 'VIEW' || primaryKeyColumns.length === 0
          ? 'なし'
          : primaryKeyColumns.map((column) => column.columnName).join(', ')}
      </p>
    </div>
  )
}