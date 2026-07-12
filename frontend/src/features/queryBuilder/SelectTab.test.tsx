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

import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listSelectableColumns } from './api'
import { SelectTab } from './SelectTab'
import type { ColumnRef, FromItem, SelectItem } from './types'

vi.mock('./api', () => ({
  listSelectableColumns: vi.fn(),
}))

const listSelectableColumnsMock = vi.mocked(listSelectableColumns)

const fromItem: FromItem = { schema: 'public', table: 'employees', alias: 't0' }
const columns: ColumnRef[] = [
  { columnName: 'id', dataType: 'INTEGER', nullable: false },
  { columnName: 'name', dataType: 'VARCHAR', nullable: true },
]

function ControlledSelectTab({ maxSelectItems }: { maxSelectItems?: number }) {
  const [selectItems, setSelectItems] = useState<SelectItem[]>([])
  return (
    <SelectTab
      connectionId={1}
      schema="public"
      fromItem={fromItem}
      joinItems={[]}
      selectItems={selectItems}
      onChange={setSelectItems}
      maxSelectItems={maxSelectItems}
    />
  )
}

describe('SelectTab', () => {
  beforeEach(() => {
    listSelectableColumnsMock.mockReset()
    listSelectableColumnsMock.mockResolvedValue(columns)
  })

  it('loads selectable columns for each table alias', async () => {
    render(<ControlledSelectTab />)

    await screen.findByText('employees（t0）の全カラムを追加')
    expect(listSelectableColumnsMock).toHaveBeenCalledWith(1, 'public', 'employees')
  })

  it('adds all columns for a table when its bulk-add button is clicked, without duplicates', async () => {
    render(<ControlledSelectTab />)
    await screen.findByText('employees（t0）の全カラムを追加')

    fireEvent.click(screen.getByText('employees（t0）の全カラムを追加'))
    expect(await screen.findAllByTestId('select-tab-item')).toHaveLength(2)

    fireEvent.click(screen.getByText('employees（t0）の全カラムを追加'))
    expect(screen.getAllByTestId('select-tab-item')).toHaveLength(2)
  })

  it('shows an error and skips the addition when it would exceed maxSelectItems', async () => {
    render(<ControlledSelectTab maxSelectItems={1} />)
    await screen.findByText('employees（t0）の全カラムを追加')

    fireEvent.click(screen.getByText('employees（t0）の全カラムを追加'))

    expect(screen.getByTestId('select-tab-error')).toHaveTextContent('上限（1件）')
    expect(screen.queryAllByTestId('select-tab-item')).toHaveLength(0)
  })

  it('adds a single blank item via the add-item button', async () => {
    render(<ControlledSelectTab />)
    await screen.findByText('employees（t0）の全カラムを追加')

    fireEvent.click(screen.getByText('項目を追加'))

    expect(screen.getAllByTestId('select-tab-item')).toHaveLength(1)
  })

  it('removes an item when its delete button is clicked', async () => {
    render(<ControlledSelectTab />)
    await screen.findByText('employees（t0）の全カラムを追加')
    fireEvent.click(screen.getByText('項目を追加'))
    expect(screen.getAllByTestId('select-tab-item')).toHaveLength(1)

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryAllByTestId('select-tab-item')).toHaveLength(0)
  })
})