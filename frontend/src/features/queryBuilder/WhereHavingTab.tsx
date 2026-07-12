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
import type { AggregateFunction, ColumnRef, Condition, FromItem, JoinItem, Operator } from './types'

const OPERATORS: Operator[] = ['EQ', 'NE', 'GT', 'LT', 'GE', 'LE', 'LIKE', 'IS_NULL', 'IS_NOT_NULL']
const AGGREGATE_FUNCTIONS: AggregateFunction[] = ['NONE', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX']

interface TableAliasRef {
  alias: string
  table: string
}

interface WhereHavingTabProps {
  connectionId: number
  schema: string
  fromItem: FromItem | null
  joinItems: JoinItem[]
  target: 'where' | 'having'
  conditions: Condition[]
  onChange: (conditions: Condition[]) => void
}

export function WhereHavingTab({
  connectionId,
  schema,
  fromItem,
  joinItems,
  target,
  conditions,
  onChange,
}: WhereHavingTabProps) {
  const [columnsByAlias, setColumnsByAlias] = useState<Record<string, ColumnRef[]>>({})

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

  const handleAdd = () => {
    if (tableRefs.length === 0) {
      return
    }
    const alias = tableRefs[0].alias
    const firstColumn = columnsByAlias[alias]?.[0]?.columnName ?? ''
    onChange([
      ...conditions,
      { tableAlias: alias, columnName: firstColumn, aggregateFunction: 'NONE', operator: 'EQ', value: '' },
    ])
  }

  const handleChange = (index: number, condition: Condition) => {
    onChange(conditions.map((c, i) => (i === index ? condition : c)))
  }

  const handleRemove = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index))
  }

  return (
    <div className="where-having-tab" data-testid={`where-having-tab-${target}`}>
      <fieldset>
        <legend>{target === 'where' ? 'WHERE条件（AND結合のみ）' : 'HAVING条件（AND結合のみ）'}</legend>
        {conditions.map((condition, index) => (
          <div key={index} className="where-having-tab-condition" data-testid="where-having-tab-condition">
            <select
              value={condition.tableAlias}
              onChange={(e) => handleChange(index, { ...condition, tableAlias: e.target.value, columnName: '' })}
            >
              {tableRefs.map((ref) => (
                <option key={ref.alias} value={ref.alias}>
                  {ref.alias}
                </option>
              ))}
            </select>
            <select
              value={condition.columnName}
              onChange={(e) => handleChange(index, { ...condition, columnName: e.target.value })}
            >
              <option value="" disabled>
                選択してください
              </option>
              {(columnsByAlias[condition.tableAlias] ?? []).map((c) => (
                <option key={c.columnName} value={c.columnName}>
                  {c.columnName}
                </option>
              ))}
            </select>
            {target === 'having' && (
              <select
                value={condition.aggregateFunction}
                onChange={(e) =>
                  handleChange(index, { ...condition, aggregateFunction: e.target.value as AggregateFunction })
                }
              >
                {AGGREGATE_FUNCTIONS.map((fn) => (
                  <option key={fn} value={fn}>
                    {fn}
                  </option>
                ))}
              </select>
            )}
            <select
              value={condition.operator}
              onChange={(e) => handleChange(index, { ...condition, operator: e.target.value as Operator })}
            >
              {OPERATORS.map((op) => (
                <option key={op} value={op}>
                  {op}
                </option>
              ))}
            </select>
            {condition.operator !== 'IS_NULL' && condition.operator !== 'IS_NOT_NULL' && (
              <input
                type="text"
                value={condition.value == null ? '' : String(condition.value)}
                onChange={(e) => handleChange(index, { ...condition, value: e.target.value })}
              />
            )}
            <button type="button" onClick={() => handleRemove(index)}>
              削除
            </button>
          </div>
        ))}
        <button type="button" onClick={handleAdd} disabled={tableRefs.length === 0}>
          条件を追加
        </button>
      </fieldset>
    </div>
  )
}