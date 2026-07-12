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
import type { AggregateFunction, ColumnRef, FromItem, JoinItem, OrderByItem, SortDirection } from './types'

const AGGREGATE_FUNCTIONS: AggregateFunction[] = ['NONE', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX']
const SORT_DIRECTIONS: SortDirection[] = ['ASC', 'DESC']

interface TableAliasRef {
  alias: string
  table: string
}

interface GroupByOrderByTabProps {
  connectionId: number
  schema: string
  fromItem: FromItem | null
  joinItems: JoinItem[]
  target: 'groupBy' | 'orderBy'
  groupByColumns?: string[]
  orderByItems?: OrderByItem[]
  onChange: (value: string[] | OrderByItem[]) => void
}

export function GroupByOrderByTab({
  connectionId,
  schema,
  fromItem,
  joinItems,
  target,
  groupByColumns = [],
  orderByItems = [],
  onChange,
}: GroupByOrderByTabProps) {
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

  const handleAddGroupBy = () => {
    if (tableRefs.length === 0) {
      return
    }
    const alias = tableRefs[0].alias
    const firstColumn = columnsByAlias[alias]?.[0]?.columnName ?? ''
    onChange([...groupByColumns, `${alias}.${firstColumn}`])
  }

  const handleChangeGroupBy = (index: number, alias: string, column: string) => {
    onChange(groupByColumns.map((c, i) => (i === index ? `${alias}.${column}` : c)))
  }

  const handleRemoveGroupBy = (index: number) => {
    onChange(groupByColumns.filter((_, i) => i !== index))
  }

  const handleAddOrderBy = () => {
    if (tableRefs.length === 0) {
      return
    }
    const alias = tableRefs[0].alias
    const firstColumn = columnsByAlias[alias]?.[0]?.columnName ?? ''
    onChange([
      ...orderByItems,
      { tableAlias: alias, columnName: firstColumn, aggregateFunction: 'NONE', direction: 'ASC' },
    ])
  }

  const handleChangeOrderBy = (index: number, item: OrderByItem) => {
    onChange(orderByItems.map((i, idx) => (idx === index ? item : i)))
  }

  const handleRemoveOrderBy = (index: number) => {
    onChange(orderByItems.filter((_, i) => i !== index))
  }

  if (target === 'groupBy') {
    return (
      <div className="group-by-order-by-tab" data-testid="group-by-order-by-tab-groupBy">
        <fieldset>
          <legend>GROUP BY</legend>
          {groupByColumns.map((column, index) => {
            const [alias, columnName] = column.split('.')
            return (
              <div key={index} className="group-by-order-by-tab-item" data-testid="group-by-order-by-tab-item">
                <select value={alias ?? ''} onChange={(e) => handleChangeGroupBy(index, e.target.value, columnName ?? '')}>
                  {tableRefs.map((ref) => (
                    <option key={ref.alias} value={ref.alias}>
                      {ref.alias}
                    </option>
                  ))}
                </select>
                <select
                  value={columnName ?? ''}
                  onChange={(e) => handleChangeGroupBy(index, alias ?? '', e.target.value)}
                >
                  <option value="" disabled>
                    選択してください
                  </option>
                  {(columnsByAlias[alias ?? ''] ?? []).map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      {c.columnName}
                    </option>
                  ))}
                </select>
                <button type="button" onClick={() => handleRemoveGroupBy(index)}>
                  削除
                </button>
              </div>
            )
          })}
          <button type="button" onClick={handleAddGroupBy} disabled={tableRefs.length === 0}>
            カラムを追加
          </button>
        </fieldset>
      </div>
    )
  }

  return (
    <div className="group-by-order-by-tab" data-testid="group-by-order-by-tab-orderBy">
      <fieldset>
        <legend>ORDER BY</legend>
        {orderByItems.map((item, index) => (
          <div key={index} className="group-by-order-by-tab-item" data-testid="group-by-order-by-tab-item">
            <select
              value={item.tableAlias}
              onChange={(e) => handleChangeOrderBy(index, { ...item, tableAlias: e.target.value, columnName: '' })}
            >
              {tableRefs.map((ref) => (
                <option key={ref.alias} value={ref.alias}>
                  {ref.alias}
                </option>
              ))}
            </select>
            <select
              value={item.columnName}
              onChange={(e) => handleChangeOrderBy(index, { ...item, columnName: e.target.value })}
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
                handleChangeOrderBy(index, { ...item, aggregateFunction: e.target.value as AggregateFunction })
              }
            >
              {AGGREGATE_FUNCTIONS.map((fn) => (
                <option key={fn} value={fn}>
                  {fn}
                </option>
              ))}
            </select>
            <select
              value={item.direction}
              onChange={(e) => handleChangeOrderBy(index, { ...item, direction: e.target.value as SortDirection })}
            >
              {SORT_DIRECTIONS.map((direction) => (
                <option key={direction} value={direction}>
                  {direction === 'ASC' ? '昇順' : '降順'}
                </option>
              ))}
            </select>
            <button type="button" onClick={() => handleRemoveOrderBy(index)}>
              削除
            </button>
          </div>
        ))}
        <button type="button" onClick={handleAddOrderBy} disabled={tableRefs.length === 0}>
          項目を追加
        </button>
      </fieldset>
    </div>
  )
}