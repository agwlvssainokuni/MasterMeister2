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
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listAccessibleConnections, listAccessibleSchemas, listAccessibleTables } from './api'
import { SchemaTableListPage } from './SchemaTableListPage'
import type { ConnectionSummary, TableSummary } from './types'

vi.mock('./api', () => ({
  listAccessibleConnections: vi.fn(),
  listAccessibleSchemas: vi.fn(),
  listAccessibleTables: vi.fn(),
}))

const listAccessibleConnectionsMock = vi.mocked(listAccessibleConnections)
const listAccessibleSchemasMock = vi.mocked(listAccessibleSchemas)
const listAccessibleTablesMock = vi.mocked(listAccessibleTables)

const connection: ConnectionSummary = { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'host1', databaseName: 'db1' }
const table: TableSummary = {
  schemaName: 'public',
  tableName: 'employees',
  tableType: 'TABLE',
  comment: null,
  effectivePermission: 'READ',
  canCreate: false,
  canDelete: false,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/master-data']}>
      <Routes>
        <Route path="/master-data" element={<SchemaTableListPage />} />
        <Route
          path="/master-data/:connectionId/:schema/:table"
          element={<div data-testid="record-list-page-stub" />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SchemaTableListPage', () => {
  beforeEach(() => {
    listAccessibleConnectionsMock.mockReset()
    listAccessibleSchemasMock.mockReset()
    listAccessibleTablesMock.mockReset()
    listAccessibleConnectionsMock.mockResolvedValue([connection])
    listAccessibleSchemasMock.mockResolvedValue(['public'])
    listAccessibleTablesMock.mockResolvedValue([table])
  })

  it('lists accessible connections and does not show the schema select until one is chosen', async () => {
    renderPage()

    await screen.findByText('conn-1')
    expect(screen.queryByTestId('schema-table-list-page-schema-select')).not.toBeInTheDocument()
  })

  it('loads schemas once a connection is selected', async () => {
    renderPage()
    await screen.findByText('conn-1')

    fireEvent.change(screen.getByTestId('schema-table-list-page-connection-select'), { target: { value: '1' } })

    expect(await screen.findByTestId('schema-table-list-page-schema-select')).toBeInTheDocument()
    expect(listAccessibleSchemasMock).toHaveBeenCalledWith(1)
  })

  it('loads and displays tables once a schema is selected', async () => {
    renderPage()
    await screen.findByText('conn-1')
    fireEvent.change(screen.getByTestId('schema-table-list-page-connection-select'), { target: { value: '1' } })
    await screen.findByTestId('schema-table-list-page-schema-select')

    fireEvent.change(screen.getByTestId('schema-table-list-page-schema-select'), { target: { value: 'public' } })

    expect(await screen.findByText('employeesを開く')).toBeInTheDocument()
    expect(listAccessibleTablesMock).toHaveBeenCalledWith(1, 'public')
  })

  it('navigates to the record list page when a table row is selected', async () => {
    renderPage()
    await screen.findByText('conn-1')
    fireEvent.change(screen.getByTestId('schema-table-list-page-connection-select'), { target: { value: '1' } })
    await screen.findByTestId('schema-table-list-page-schema-select')
    fireEvent.change(screen.getByTestId('schema-table-list-page-schema-select'), { target: { value: 'public' } })
    await screen.findByText('employeesを開く')

    fireEvent.click(screen.getByTestId('schema-table-list-page-row'))

    expect(await screen.findByTestId('record-list-page-stub')).toBeInTheDocument()
  })
})