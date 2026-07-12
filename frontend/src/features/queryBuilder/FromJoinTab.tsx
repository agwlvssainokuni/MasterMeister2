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
import { listSelectableTables } from './api'
import type { FromItem, JoinItem, JoinType, Operator, TableRef } from './types'

const JOIN_TYPES: JoinType[] = ['INNER', 'LEFT', 'RIGHT']
const ON_OPERATORS: Operator[] = ['EQ', 'NE', 'GT', 'LT', 'GE', 'LE']

interface FromJoinTabProps {
  connectionId: number
  schema: string
  fromItem: FromItem | null
  joinItems: JoinItem[]
  onChange: (fromItem: FromItem, joinItems: JoinItem[]) => void
}

export function FromJoinTab({ connectionId, schema, fromItem, joinItems, onChange }: FromJoinTabProps) {
  const [selectableTables, setSelectableTables] = useState<TableRef[]>([])

  useEffect(() => {
    listSelectableTables(connectionId, schema).then(setSelectableTables)
  }, [connectionId, schema])

  const knownAliases = [fromItem?.alias, ...joinItems.map((j) => j.alias)].filter(
    (alias): alias is string => alias != null && alias !== '',
  )

  const handleSelectBaseTable = (table: string) => {
    onChange({ schema, table, alias: fromItem?.alias ?? 't0' }, joinItems)
  }

  const handleChangeBaseAlias = (alias: string) => {
    if (fromItem == null) {
      return
    }
    onChange({ ...fromItem, alias }, joinItems)
  }

  const handleAddJoin = () => {
    if (fromItem == null || selectableTables.length === 0) {
      return
    }
    const nextJoin: JoinItem = {
      type: 'INNER',
      schema,
      table: selectableTables[0].table,
      alias: `j${joinItems.length}`,
      onCondition: {
        tableAlias: fromItem.alias,
        columnName: '',
        aggregateFunction: 'NONE',
        operator: 'EQ',
        value: `${fromItem.alias}.`,
      },
    }
    onChange(fromItem, [...joinItems, nextJoin])
  }

  const handleChangeJoin = (index: number, joinItem: JoinItem) => {
    if (fromItem == null) {
      return
    }
    onChange(fromItem, joinItems.map((j, i) => (i === index ? joinItem : j)))
  }

  const handleRemoveJoin = (index: number) => {
    if (fromItem == null) {
      return
    }
    onChange(fromItem, joinItems.filter((_, i) => i !== index))
  }

  return (
    <div className="from-join-tab" data-testid="from-join-tab">
      <fieldset>
        <legend>ベーステーブル</legend>
        <label>
          テーブル
          <select
            data-testid="from-join-tab-base-table-select"
            value={fromItem?.table ?? ''}
            onChange={(e) => handleSelectBaseTable(e.target.value)}
          >
            <option value="" disabled>
              選択してください
            </option>
            {selectableTables.map((t) => (
              <option key={t.table} value={t.table}>
                {t.table}
              </option>
            ))}
          </select>
        </label>
        <label>
          エイリアス
          <input
            type="text"
            data-testid="from-join-tab-base-alias-input"
            value={fromItem?.alias ?? ''}
            onChange={(e) => handleChangeBaseAlias(e.target.value)}
            disabled={fromItem == null}
          />
        </label>
      </fieldset>

      <fieldset>
        <legend>JOIN</legend>
        {joinItems.map((joinItem, index) => {
          const [rightAlias, rightColumn] = String(joinItem.onCondition.value ?? '').split('.')
          return (
            <div key={index} className="from-join-tab-join-item" data-testid="from-join-tab-join-item">
              <select
                value={joinItem.type}
                onChange={(e) => handleChangeJoin(index, { ...joinItem, type: e.target.value as JoinType })}
              >
                {JOIN_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <select
                value={joinItem.table}
                onChange={(e) => handleChangeJoin(index, { ...joinItem, table: e.target.value })}
              >
                {selectableTables.map((t) => (
                  <option key={t.table} value={t.table}>
                    {t.table}
                  </option>
                ))}
              </select>
              <input
                type="text"
                placeholder="エイリアス"
                value={joinItem.alias}
                onChange={(e) => handleChangeJoin(index, { ...joinItem, alias: e.target.value })}
              />
              <span>ON</span>
              <select
                value={joinItem.onCondition.tableAlias}
                onChange={(e) =>
                  handleChangeJoin(index, {
                    ...joinItem,
                    onCondition: { ...joinItem.onCondition, tableAlias: e.target.value },
                  })
                }
              >
                {knownAliases.map((alias) => (
                  <option key={alias} value={alias}>
                    {alias}
                  </option>
                ))}
              </select>
              <input
                type="text"
                placeholder="カラム"
                value={joinItem.onCondition.columnName}
                onChange={(e) =>
                  handleChangeJoin(index, {
                    ...joinItem,
                    onCondition: { ...joinItem.onCondition, columnName: e.target.value },
                  })
                }
              />
              <select
                value={joinItem.onCondition.operator}
                onChange={(e) =>
                  handleChangeJoin(index, {
                    ...joinItem,
                    onCondition: { ...joinItem.onCondition, operator: e.target.value as Operator },
                  })
                }
              >
                {ON_OPERATORS.map((op) => (
                  <option key={op} value={op}>
                    {op}
                  </option>
                ))}
              </select>
              <select
                value={rightAlias ?? ''}
                onChange={(e) =>
                  handleChangeJoin(index, {
                    ...joinItem,
                    onCondition: { ...joinItem.onCondition, value: `${e.target.value}.${rightColumn ?? ''}` },
                  })
                }
              >
                {knownAliases.map((alias) => (
                  <option key={alias} value={alias}>
                    {alias}
                  </option>
                ))}
              </select>
              <input
                type="text"
                placeholder="カラム"
                value={rightColumn ?? ''}
                onChange={(e) =>
                  handleChangeJoin(index, {
                    ...joinItem,
                    onCondition: { ...joinItem.onCondition, value: `${rightAlias ?? ''}.${e.target.value}` },
                  })
                }
              />
              <button type="button" onClick={() => handleRemoveJoin(index)}>
                削除
              </button>
            </div>
          )
        })}
        <button type="button" onClick={handleAddJoin} disabled={fromItem == null || selectableTables.length === 0}>
          JOINを追加
        </button>
      </fieldset>
    </div>
  )
}