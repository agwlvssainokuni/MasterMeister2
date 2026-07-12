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
import { WhereHavingTab } from './WhereHavingTab'
import type { Condition, FromItem } from './types'

vi.mock('./api', () => ({
  listSelectableColumns: vi.fn(),
}))

const listSelectableColumnsMock = vi.mocked(listSelectableColumns)

const fromItem: FromItem = { schema: 'public', table: 'employees', alias: 't0' }
const columns = [
  { columnName: 'id', dataType: 'INTEGER', nullable: false },
  { columnName: 'name', dataType: 'VARCHAR', nullable: true },
]

function ControlledWhereHavingTab({ target }: { target: 'where' | 'having' }) {
  const [conditions, setConditions] = useState<Condition[]>([])
  return (
    <WhereHavingTab
      connectionId={1}
      schema="public"
      fromItem={fromItem}
      joinItems={[]}
      target={target}
      conditions={conditions}
      onChange={setConditions}
    />
  )
}

describe('WhereHavingTab', () => {
  beforeEach(() => {
    listSelectableColumnsMock.mockReset()
    listSelectableColumnsMock.mockResolvedValue(columns)
  })

  it('renders the WHERE legend for target="where"', () => {
    render(<ControlledWhereHavingTab target="where" />)
    expect(screen.getByText('WHERE条件（AND結合のみ）')).toBeInTheDocument()
  })

  it('renders the HAVING legend for target="having"', () => {
    render(<ControlledWhereHavingTab target="having" />)
    expect(screen.getByText('HAVING条件（AND結合のみ）')).toBeInTheDocument()
  })

  it('adds a condition with a default EQ operator', async () => {
    render(<ControlledWhereHavingTab target="where" />)
    await screen.findByText('条件を追加')

    fireEvent.click(screen.getByText('条件を追加'))

    expect(screen.getByTestId('where-having-tab-condition')).toBeInTheDocument()
    expect(screen.getByDisplayValue('EQ')).toBeInTheDocument()
  })

  it('hides the value input for the IS_NULL operator', async () => {
    render(<ControlledWhereHavingTab target="where" />)
    await screen.findByText('条件を追加')
    fireEvent.click(screen.getByText('条件を追加'))
    expect(screen.getAllByRole('textbox')).toHaveLength(1)

    const operatorSelect = screen.getByDisplayValue('EQ')
    fireEvent.change(operatorSelect, { target: { value: 'IS_NULL' } })

    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
  })

  it('shows the aggregate function select only for target="having"', async () => {
    render(<ControlledWhereHavingTab target="having" />)
    await screen.findByText('条件を追加')

    fireEvent.click(screen.getByText('条件を追加'))

    expect(screen.getByDisplayValue('NONE')).toBeInTheDocument()
  })

  it('removes a condition when its delete button is clicked', async () => {
    render(<ControlledWhereHavingTab target="where" />)
    await screen.findByText('条件を追加')
    fireEvent.click(screen.getByText('条件を追加'))
    expect(screen.getByTestId('where-having-tab-condition')).toBeInTheDocument()

    fireEvent.click(screen.getByText('削除'))

    expect(screen.queryByTestId('where-having-tab-condition')).not.toBeInTheDocument()
  })
})