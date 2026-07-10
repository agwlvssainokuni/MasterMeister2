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
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ConnectionTable } from './ConnectionTable'
import type { ConnectionSummary } from './types'

const connections: ConnectionSummary[] = [
  { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'host1', databaseName: 'db1' },
  { id: 2, name: 'conn-2', rdbmsType: 'POSTGRESQL', host: 'host2', databaseName: 'db2' },
]

function renderTable(onTest = vi.fn(), onImportSchema = vi.fn()) {
  return render(
    <MemoryRouter>
      <ConnectionTable connections={connections} onTest={onTest} onImportSchema={onImportSchema} />
    </MemoryRouter>,
  )
}

describe('ConnectionTable', () => {
  it('renders a row for each connection', () => {
    renderTable()

    expect(screen.getByText('conn-1')).toBeInTheDocument()
    expect(screen.getByText('conn-2')).toBeInTheDocument()
  })

  it('links the edit action to the connection detail route', () => {
    renderTable()

    const editLinks = screen.getAllByTestId('connection-table-edit-button')
    expect(editLinks[0]).toHaveAttribute('href', '/admin/rdbms-connections/1')
    expect(editLinks[1]).toHaveAttribute('href', '/admin/rdbms-connections/2')
  })

  it('calls onTest with the connection id when the test button is clicked', () => {
    const onTest = vi.fn()
    renderTable(onTest)

    fireEvent.click(screen.getAllByTestId('connection-table-test-button')[1])

    expect(onTest).toHaveBeenCalledWith(2)
  })

  it('calls onImportSchema with the connection id when the import-schema button is clicked', () => {
    const onImportSchema = vi.fn()
    renderTable(vi.fn(), onImportSchema)

    fireEvent.click(screen.getAllByTestId('connection-table-import-schema-button')[0])

    expect(onImportSchema).toHaveBeenCalledWith(1)
  })
})