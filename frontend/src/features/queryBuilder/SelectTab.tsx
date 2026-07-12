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
import { listSelectableColumns } from './api'
import type { AggregateFunction, ColumnRef, FromItem, JoinItem, SelectItem } from './types'

const AGGREGATE_FUNCTIONS: AggregateFunction[] = ['NONE', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX']
const DEFAULT_MAX_SELECT_ITEMS = 100

interface TableAliasRef {
  alias: string
  table: string
}

interface SelectTabProps {
  connectionId: number
  schema: string
  fromItem: FromItem | null
  joinItems: JoinItem[]
  selectItems: SelectItem[]
  onChange: (items: SelectItem[]) => void
  maxSelectItems?: number
}

export function SelectTab({
  connectionId,
  schema,
  fromItem,
  joinItems,
  selectItems,
  onChange,
  maxSelectItems = DEFAULT_MAX_SELECT_ITEMS,
}: SelectTabProps) {
  const [columnsByAlias, setColumnsByAlias] = useState<Record<string, ColumnRef[]>>({})
  const [error, setError] = useState<string | null>(null)

  const tableRefs: TableAliasRef[] = [
    ...(fromItem != null ? [{ alias: fromItem.alias, table: fromItem.table }] : []),
    ...joinItems.map((j) => ({ alias: j.alias, table: j.table })),
  ]
  const tableRefsKey = tableRefs.map((ref) => `${ref.alias}:${ref.table}`).join(',')

  useEffect(() => {
    Promise.all(
      tableRefs.map((ref) =>
        listSelectableColumns(connectionId, schema, ref.table).then((columns) => [ref.alias, columns] as const),
      ),
    ).then((entries) => setColumnsByAlias(Object.fromEntries(entries)))
    // tableRefsKeyの変更時のみ再取得する（tableRefsは毎レンダーで新規配列のため依存に使わない）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionId, schema, tableRefsKey])

  const handleAddAllColumns = (alias: string) => {
    const columns = columnsByAlias[alias] ?? []
    const existingKeys = new Set(selectItems.map((i) => `${i.tableAlias}.${i.columnName}`))
    const toAdd = columns
      .filter((c) => !existingKeys.has(`${alias}.${c.columnName}`))
      .map((c): SelectItem => ({
        tableAlias: alias, columnName: c.columnName, aggregateFunction: 'NONE', outputAlias: null,
      }))
    if (selectItems.length + toAdd.length > maxSelectItems) {
      setError(`SELECT項目が上限（${maxSelectItems}件）を超えるため追加できません。`)
      return
    }
    setError(null)
    onChange([...selectItems, ...toAdd])
  }

  const handleAddItem = () => {
    if (tableRefs.length === 0) {
      return
    }
    if (selectItems.length + 1 > maxSelectItems) {
      setError(`SELECT項目が上限（${maxSelectItems}件）を超えるため追加できません。`)
      return
    }
    setError(null)
    const alias = tableRefs[0].alias
    const firstColumn = columnsByAlias[alias]?.[0]?.columnName ?? ''
    onChange([
      ...selectItems,
      { tableAlias: alias, columnName: firstColumn, aggregateFunction: 'NONE', outputAlias: null },
    ])
  }

  const handleChangeItem = (index: number, item: SelectItem) => {
    onChange(selectItems.map((i, idx) => (idx === index ? item : i)))
  }

  const handleRemoveItem = (index: number) => {
    onChange(selectItems.filter((_, idx) => idx !== index))
  }

  return (
    <div className="select-tab" data-testid="select-tab">
      <fieldset>
        <legend>SELECT項目</legend>

        {tableRefs.map((ref) => (
          <button key={ref.alias} type="button" onClick={() => handleAddAllColumns(ref.alias)}>
            {ref.table}（{ref.alias}）の全カラムを追加
          </button>
        ))}

        {error != null && (
          <p className="select-tab-error" data-testid="select-tab-error">
            {error}
          </p>
        )}

        {selectItems.map((item, index) => (
          <div key={index} className="select-tab-item" data-testid="select-tab-item">
            <select
              value={item.tableAlias}
              onChange={(e) => handleChangeItem(index, { ...item, tableAlias: e.target.value, columnName: '' })}
            >
              {tableRefs.map((ref) => (
                <option key={ref.alias} value={ref.alias}>
                  {ref.alias}
                </option>
              ))}
            </select>
            <select
              value={item.columnName}
              onChange={(e) => handleChangeItem(index, { ...item, columnName: e.target.value })}
            >
              <option value="" disabled>
                選択してください
              </option>
              {(columnsByAlias[item.tableAlias] ?? []).map((c) => (
                <option key={c.columnName} value={c.columnName}>
                  {c.columnName}
                </option>
              ))}
            </select>
            <select
              value={item.aggregateFunction}
              onChange={(e) =>
                handleChangeItem(index, { ...item, aggregateFunction: e.target.value as AggregateFunction })
              }
            >
              {AGGREGATE_FUNCTIONS.map((fn) => (
                <option key={fn} value={fn}>
                  {fn}
                </option>
              ))}
            </select>
            <input
              type="text"
              placeholder="出力エイリアス"
              value={item.outputAlias ?? ''}
              onChange={(e) => handleChangeItem(index, { ...item, outputAlias: e.target.value || null })}
            />
            <button type="button" onClick={() => handleRemoveItem(index)}>
              削除
            </button>
          </div>
        ))}
        <button type="button" onClick={handleAddItem} disabled={tableRefs.length === 0}>
          項目を追加
        </button>
      </fieldset>
    </div>
  )
}