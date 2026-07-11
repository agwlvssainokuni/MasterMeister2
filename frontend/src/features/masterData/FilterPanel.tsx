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

import { useState } from 'react'
import type { ColumnMetadata, FilterCriteria, FilterMode, Operator, SortDirection, UiCondition, UiSort } from './types'

const OPERATORS: Operator[] = ['EQ', 'NE', 'GT', 'LT', 'GE', 'LE', 'LIKE', 'IS_NULL', 'IS_NOT_NULL']

const PERMISSION_ORDER: Record<string, number> = { NONE: 0, READ: 1, UPDATE: 2 }

interface FilterPanelProps {
  columns: ColumnMetadata[]
  criteria: FilterCriteria
  onChange: (criteria: FilterCriteria) => void
}

export function FilterPanel({ columns, criteria, onChange }: FilterPanelProps) {
  const [mode, setMode] = useState<FilterMode>(criteria.mode)

  const readableColumns = columns.filter((c) => PERMISSION_ORDER[c.effectivePermission] >= PERMISSION_ORDER.READ)

  const handleModeChange = (nextMode: FilterMode) => {
    setMode(nextMode)
    onChange({ ...criteria, mode: nextMode })
  }

  const handleAddCondition = () => {
    if (readableColumns.length === 0) {
      return
    }
    const nextCondition: UiCondition = { columnName: readableColumns[0].columnName, operator: 'EQ', value: '' }
    onChange({ ...criteria, uiConditions: [...criteria.uiConditions, nextCondition] })
  }

  const handleChangeCondition = (index: number, condition: UiCondition) => {
    onChange({ ...criteria, uiConditions: criteria.uiConditions.map((c, i) => (i === index ? condition : c)) })
  }

  const handleRemoveCondition = (index: number) => {
    onChange({ ...criteria, uiConditions: criteria.uiConditions.filter((_, i) => i !== index) })
  }

  const handleAddSort = () => {
    if (readableColumns.length === 0) {
      return
    }
    const nextSort: UiSort = { columnName: readableColumns[0].columnName, direction: 'ASC' }
    onChange({ ...criteria, uiSorts: [...criteria.uiSorts, nextSort] })
  }

  const handleChangeSort = (index: number, sort: UiSort) => {
    onChange({ ...criteria, uiSorts: criteria.uiSorts.map((s, i) => (i === index ? sort : s)) })
  }

  const handleRemoveSort = (index: number) => {
    onChange({ ...criteria, uiSorts: criteria.uiSorts.filter((_, i) => i !== index) })
  }

  return (
    <div className="filter-panel" data-testid="filter-panel">
      <div className="filter-panel-mode-toggle">
        <label>
          <input
            type="radio"
            name="filter-panel-mode"
            value="UI"
            checked={mode === 'UI'}
            onChange={() => handleModeChange('UI')}
          />
          UIモード
        </label>
        <label>
          <input
            type="radio"
            name="filter-panel-mode"
            value="RAW"
            checked={mode === 'RAW'}
            onChange={() => handleModeChange('RAW')}
          />
          RAWモード
        </label>
      </div>

      {mode === 'UI' ? (
        <div className="filter-panel-ui-mode" data-testid="filter-panel-ui-mode">
          <fieldset>
            <legend>絞り込み条件</legend>
            {criteria.uiConditions.map((condition, index) => (
              <div key={index} className="filter-panel-condition">
                <select
                  value={condition.columnName}
                  onChange={(e) => handleChangeCondition(index, { ...condition, columnName: e.target.value })}
                >
                  {readableColumns.map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      {c.columnName}
                    </option>
                  ))}
                </select>
                <select
                  value={condition.operator}
                  onChange={(e) =>
                    handleChangeCondition(index, { ...condition, operator: e.target.value as Operator })
                  }
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
                    onChange={(e) => handleChangeCondition(index, { ...condition, value: e.target.value })}
                  />
                )}
                <button type="button" onClick={() => handleRemoveCondition(index)}>
                  削除
                </button>
              </div>
            ))}
            <button type="button" onClick={handleAddCondition} disabled={readableColumns.length === 0}>
              条件を追加
            </button>
          </fieldset>

          <fieldset>
            <legend>ソート条件</legend>
            {criteria.uiSorts.map((sort, index) => (
              <div key={index} className="filter-panel-sort">
                <select
                  value={sort.columnName}
                  onChange={(e) => handleChangeSort(index, { ...sort, columnName: e.target.value })}
                >
                  {readableColumns.map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      {c.columnName}
                    </option>
                  ))}
                </select>
                <select
                  value={sort.direction}
                  onChange={(e) => handleChangeSort(index, { ...sort, direction: e.target.value as SortDirection })}
                >
                  <option value="ASC">昇順</option>
                  <option value="DESC">降順</option>
                </select>
                <button type="button" onClick={() => handleRemoveSort(index)}>
                  削除
                </button>
              </div>
            ))}
            <button type="button" onClick={handleAddSort} disabled={readableColumns.length === 0}>
              ソートを追加
            </button>
          </fieldset>
        </div>
      ) : (
        <div className="filter-panel-raw-mode" data-testid="filter-panel-raw-mode">
          <label>
            WHERE句
            <input
              type="text"
              data-testid="filter-panel-raw-where"
              value={criteria.rawWhere ?? ''}
              onChange={(e) => onChange({ ...criteria, rawWhere: e.target.value })}
            />
          </label>
          <label>
            ORDER BY句
            <input
              type="text"
              data-testid="filter-panel-raw-order-by"
              value={criteria.rawOrderBy ?? ''}
              onChange={(e) => onChange({ ...criteria, rawOrderBy: e.target.value })}
            />
          </label>
        </div>
      )}
    </div>
  )
}