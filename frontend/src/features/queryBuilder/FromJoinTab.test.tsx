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
import { listSelectableTables } from './api'
import { FromJoinTab } from './FromJoinTab'
import type { FromItem, JoinItem, TableRef } from './types'

vi.mock('./api', () => ({
  listSelectableTables: vi.fn(),
}))

const listSelectableTablesMock = vi.mocked(listSelectableTables)

const tables: TableRef[] = [
  { schema: 'public', table: 'employees', comment: null },
  { schema: 'public', table: 'departments', comment: null },
]

function ControlledFromJoinTab() {
  const [fromItem, setFromItem] = useState<FromItem | null>(null)
  const [joinItems, setJoinItems] = useState<JoinItem[]>([])
  return (
    <FromJoinTab
      connectionId={1}
      schema="public"
      fromItem={fromItem}
      joinItems={joinItems}
      onChange={(nextFromItem, nextJoinItems) => {
        setFromItem(nextFromItem)
        setJoinItems(nextJoinItems)
      }}
    />
  )
}

describe('FromJoinTab', () => {
  beforeEach(() => {
    listSelectableTablesMock.mockReset()
    listSelectableTablesMock.mockResolvedValue(tables)
  })

  it('loads selectable tables for the given connection/schema', async () => {
    render(<ControlledFromJoinTab />)

    await screen.findByText('employees')
    expect(listSelectableTablesMock).toHaveBeenCalledWith(1, 'public')
  })

  it('selecting a base table sets fromItem with a default alias', async () => {
    render(<ControlledFromJoinTab />)
    await screen.findByText('employees')

    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    expect(screen.getByTestId('from-join-tab-base-alias-input')).toHaveValue('t0')
  })

  it('changing the base alias updates fromItem.alias', async () => {
    render(<ControlledFromJoinTab />)
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    fireEvent.change(screen.getByTestId('from-join-tab-base-alias-input'), { target: { value: 'e' } })

    expect(screen.getByTestId('from-join-tab-base-alias-input')).toHaveValue('e')
  })

  it('adds a JOIN item with a default INNER type once a base table is selected', async () => {
    render(<ControlledFromJoinTab />)
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    fireEvent.click(screen.getByText('JOINを追加'))

    expect(screen.getByTestId('from-join-tab-join-item')).toBeInTheDocument()
    expect(screen.getByDisplayValue('INNER')).toBeInTheDocument()
  })

  it('removes a JOIN item when its delete button is clicked', async () => {
    render(<ControlledFromJoinTab />)
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    fireEvent.click(screen.getByText('JOINを追加'))
    expect(screen.getByTestId('from-join-tab-join-item')).toBeInTheDocument()

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryByTestId('from-join-tab-join-item')).not.toBeInTheDocument()
  })

  it('disables the JOIN button until a base table is selected', async () => {
    render(<ControlledFromJoinTab />)
    await screen.findByText('employees')

    expect(screen.getByText('JOINを追加')).toBeDisabled()
  })
})