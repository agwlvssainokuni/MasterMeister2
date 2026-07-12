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
import { GroupByOrderByTab } from './GroupByOrderByTab'
import type { FromItem, OrderByItem } from './types'

vi.mock('./api', () => ({
  listSelectableColumns: vi.fn(),
}))

const listSelectableColumnsMock = vi.mocked(listSelectableColumns)

const fromItem: FromItem = { schema: 'public', table: 'employees', alias: 't0' }
const columns = [
  { columnName: 'id', dataType: 'INTEGER', nullable: false },
  { columnName: 'name', dataType: 'VARCHAR', nullable: true },
]

function ControlledGroupByTab() {
  const [groupByColumns, setGroupByColumns] = useState<string[]>([])
  return (
    <GroupByOrderByTab
      connectionId={1}
      schema="public"
      fromItem={fromItem}
      joinItems={[]}
      target="groupBy"
      groupByColumns={groupByColumns}
      onChange={(value) => setGroupByColumns(value as string[])}
    />
  )
}

function ControlledOrderByTab() {
  const [orderByItems, setOrderByItems] = useState<OrderByItem[]>([])
  return (
    <GroupByOrderByTab
      connectionId={1}
      schema="public"
      fromItem={fromItem}
      joinItems={[]}
      target="orderBy"
      orderByItems={orderByItems}
      onChange={(value) => setOrderByItems(value as OrderByItem[])}
    />
  )
}

describe('GroupByOrderByTab', () => {
  beforeEach(() => {
    listSelectableColumnsMock.mockReset()
    listSelectableColumnsMock.mockResolvedValue(columns)
  })

  it('adds a "alias.column" entry for target="groupBy"', async () => {
    render(<ControlledGroupByTab />)
    await screen.findByText('カラムを追加')

    fireEvent.click(screen.getByText('カラムを追加'))

    expect(screen.getByTestId('group-by-order-by-tab-item')).toBeInTheDocument()
    expect(screen.getByDisplayValue('t0')).toBeInTheDocument()
    expect(screen.getByDisplayValue('id')).toBeInTheDocument()
  })

  it('removes a groupBy entry when its delete button is clicked', async () => {
    render(<ControlledGroupByTab />)
    await screen.findByText('カラムを追加')
    fireEvent.click(screen.getByText('カラムを追加'))
    expect(screen.getByTestId('group-by-order-by-tab-item')).toBeInTheDocument()

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryByTestId('group-by-order-by-tab-item')).not.toBeInTheDocument()
  })

  it('adds an OrderByItem with ASC direction by default for target="orderBy"', async () => {
    render(<ControlledOrderByTab />)
    await screen.findByText('項目を追加')

    fireEvent.click(screen.getByText('項目を追加'))

    expect(screen.getByTestId('group-by-order-by-tab-item')).toBeInTheDocument()
    expect(screen.getByDisplayValue('昇順')).toBeInTheDocument()
  })

  it('changes the sort direction to DESC', async () => {
    render(<ControlledOrderByTab />)
    await screen.findByText('項目を追加')
    fireEvent.click(screen.getByText('項目を追加'))

    fireEvent.change(screen.getByDisplayValue('昇順'), { target: { value: 'DESC' } })

    expect(screen.getByDisplayValue('降順')).toBeInTheDocument()
  })

  it('removes an orderBy entry when its delete button is clicked', async () => {
    render(<ControlledOrderByTab />)
    await screen.findByText('項目を追加')
    fireEvent.click(screen.getByText('項目を追加'))
    expect(screen.getByTestId('group-by-order-by-tab-item')).toBeInTheDocument()

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryByTestId('group-by-order-by-tab-item')).not.toBeInTheDocument()
  })
})