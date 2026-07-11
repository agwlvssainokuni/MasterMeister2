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

import { render, screen, fireEvent } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { FilterPanel } from './FilterPanel'
import type { ColumnMetadata, FilterCriteria } from './types'

const EMPTY_CRITERIA: FilterCriteria = { mode: 'UI', uiConditions: [], uiSorts: [], rawWhere: null, rawOrderBy: null }

const columns: ColumnMetadata[] = [
  { columnName: 'id', dataType: 'INTEGER', nullable: false, primaryKeySequence: 1, effectivePermission: 'UPDATE' },
  { columnName: 'name', dataType: 'VARCHAR', nullable: true, primaryKeySequence: null, effectivePermission: 'READ' },
  { columnName: 'secret', dataType: 'VARCHAR', nullable: true, primaryKeySequence: null, effectivePermission: 'NONE' },
]

function ControlledFilterPanel({ columns: cols }: { columns: ColumnMetadata[] }) {
  const [criteria, setCriteria] = useState<FilterCriteria>(EMPTY_CRITERIA)
  return <FilterPanel columns={cols} criteria={criteria} onChange={setCriteria} />
}

describe('FilterPanel', () => {
  it('shows the UI mode panel by default and hides it when switching to RAW mode', () => {
    render(<ControlledFilterPanel columns={columns} />)

    expect(screen.getByTestId('filter-panel-ui-mode')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('RAWモード'))

    expect(screen.queryByTestId('filter-panel-ui-mode')).not.toBeInTheDocument()
    expect(screen.getByTestId('filter-panel-raw-mode')).toBeInTheDocument()
  })

  it('excludes NONE-permission columns from the condition/sort column selects', () => {
    render(<ControlledFilterPanel columns={columns} />)

    fireEvent.click(screen.getByText('条件を追加'))

    const selects = screen.getAllByRole('combobox')
    const columnSelect = selects[0]
    const optionValues = Array.from(columnSelect.querySelectorAll('option')).map((o) => o.textContent)
    expect(optionValues).toEqual(['id', 'name'])
    expect(optionValues).not.toContain('secret')
  })

  it('adds a condition and hides the value input for IS_NULL operator', () => {
    render(<ControlledFilterPanel columns={columns} />)

    fireEvent.click(screen.getByText('条件を追加'))
    expect(screen.getAllByRole('textbox')).toHaveLength(1)

    const operatorSelect = screen.getAllByRole('combobox')[1]
    fireEvent.change(operatorSelect, { target: { value: 'IS_NULL' } })

    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
  })

  it('removes a condition when its delete button is clicked', () => {
    render(<ControlledFilterPanel columns={columns} />)

    fireEvent.click(screen.getByText('条件を追加'))
    expect(screen.getAllByRole('textbox')).toHaveLength(1)

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
  })

  it('adds a sort condition with ASC direction by default', () => {
    render(<ControlledFilterPanel columns={columns} />)

    fireEvent.click(screen.getByText('ソートを追加'))

    expect(screen.getByText('昇順')).toBeInTheDocument()
  })

  it('updates rawWhere/rawOrderBy text inputs in RAW mode', () => {
    render(<ControlledFilterPanel columns={columns} />)
    fireEvent.click(screen.getByLabelText('RAWモード'))

    fireEvent.change(screen.getByTestId('filter-panel-raw-where'), { target: { value: 'id > 10' } })
    fireEvent.change(screen.getByTestId('filter-panel-raw-order-by'), { target: { value: 'id DESC' } })

    expect(screen.getByTestId('filter-panel-raw-where')).toHaveValue('id > 10')
    expect(screen.getByTestId('filter-panel-raw-order-by')).toHaveValue('id DESC')
  })

  it('disables the add-condition/add-sort buttons when there are no readable columns', () => {
    render(<ControlledFilterPanel columns={[columns[2]]} />)

    expect(screen.getByText('条件を追加')).toBeDisabled()
    expect(screen.getByText('ソートを追加')).toBeDisabled()
  })

  it('propagates onChange calls to a parent-supplied handler', () => {
    const onChange = vi.fn()
    render(<FilterPanel columns={columns} criteria={EMPTY_CRITERIA} onChange={onChange} />)

    fireEvent.click(screen.getByLabelText('RAWモード'))

    expect(onChange).toHaveBeenCalledWith({ ...EMPTY_CRITERIA, mode: 'RAW' })
  })
})