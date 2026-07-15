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
import { useConnectionStore } from '../../store/connectionStore'
import { listAccessibleSchemas, listAccessibleTables } from './api'
import { SchemaTableListPage } from './SchemaTableListPage'
import type { TableSummary } from './types'

vi.mock('./api', () => ({
  listAccessibleSchemas: vi.fn(),
  listAccessibleTables: vi.fn(),
}))

const listAccessibleSchemasMock = vi.mocked(listAccessibleSchemas)
const listAccessibleTablesMock = vi.mocked(listAccessibleTables)

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
    listAccessibleSchemasMock.mockReset()
    listAccessibleTablesMock.mockReset()
    listAccessibleSchemasMock.mockResolvedValue(['public'])
    listAccessibleTablesMock.mockResolvedValue([table])
    useConnectionStore.setState({ connectionId: null, connections: [] })
  })

  it('shows a message when connectionId is not set in the global connection context', () => {
    renderPage()

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
    expect(listAccessibleSchemasMock).not.toHaveBeenCalled()
  })

  it('loads schemas for the connectionId from the global connection context', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })

    renderPage()

    expect(await screen.findByTestId('schema-table-list-page-schema-select')).toBeInTheDocument()
    expect(listAccessibleSchemasMock).toHaveBeenCalledWith(1)
  })

  it('loads and displays tables once a schema is selected', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await screen.findByTestId('schema-table-list-page-schema-select')

    fireEvent.change(screen.getByTestId('schema-table-list-page-schema-select'), { target: { value: 'public' } })

    expect(await screen.findByText('employeesを開く')).toBeInTheDocument()
    expect(listAccessibleTablesMock).toHaveBeenCalledWith(1, 'public')
  })

  it('navigates to the record list page when a table row is selected', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await screen.findByTestId('schema-table-list-page-schema-select')
    fireEvent.change(screen.getByTestId('schema-table-list-page-schema-select'), { target: { value: 'public' } })
    await screen.findByText('employeesを開く')

    fireEvent.click(screen.getByTestId('schema-table-list-page-row'))

    expect(await screen.findByTestId('record-list-page-stub')).toBeInTheDocument()
  })
})