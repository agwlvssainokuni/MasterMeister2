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
import { describe, expect, it, vi } from 'vitest'
import { TableList } from './TableList'
import type { TableMetadata } from './types'

const tables: TableMetadata[] = [
  { schemaName: 'public', tableName: 'users', tableType: 'TABLE', comment: 'ユーザー' },
  { schemaName: 'public', tableName: 'user_view', tableType: 'VIEW', comment: null },
]

describe('TableList', () => {
  it('renders a row for each table', () => {
    render(<TableList tables={tables} onSelect={vi.fn()} />)

    expect(screen.getByText('users')).toBeInTheDocument()
    expect(screen.getByText('user_view')).toBeInTheDocument()
  })

  it('calls onSelect with the chosen table when a row is clicked', () => {
    const onSelect = vi.fn()
    render(<TableList tables={tables} onSelect={onSelect} />)

    fireEvent.click(screen.getAllByTestId('table-list-row')[1])

    expect(onSelect).toHaveBeenCalledWith(tables[1])
  })
})
