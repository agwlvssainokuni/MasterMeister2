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
import { getTableDetail, listSchemas, listTables } from './api'
import { SchemaBrowserPage } from './SchemaBrowserPage'
import type { TableDetail, TableMetadata } from './types'

vi.mock('./api', () => ({
  listSchemas: vi.fn(),
  listTables: vi.fn(),
  getTableDetail: vi.fn(),
}))

const listSchemasMock = vi.mocked(listSchemas)
const listTablesMock = vi.mocked(listTables)
const getTableDetailMock = vi.mocked(getTableDetail)

const tables: TableMetadata[] = [{ schemaName: 'public', tableName: 'users', tableType: 'TABLE', comment: null }]
const detail: TableDetail = {
  schemaName: 'public',
  tableName: 'users',
  tableType: 'TABLE',
  comment: null,
  columns: [
    { columnName: 'id', dataType: 'INTEGER', nullable: false, comment: null, ordinalPosition: 1, primaryKeySequence: 1 },
  ],
}

function renderAt(connectionId: string) {
  return render(
    <MemoryRouter initialEntries={[`/admin/schema/${connectionId}`]}>
      <Routes>
        <Route path="/admin/schema/:connectionId" element={<SchemaBrowserPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SchemaBrowserPage', () => {
  beforeEach(() => {
    listSchemasMock.mockReset()
    listTablesMock.mockReset()
    getTableDetailMock.mockReset()
    listSchemasMock.mockResolvedValue(['public', 'app'])
    listTablesMock.mockResolvedValue(tables)
    getTableDetailMock.mockResolvedValue(detail)
  })

  it('loads schemas for the connection on mount', async () => {
    renderAt('42')

    await screen.findByText('public')
    expect(listSchemasMock).toHaveBeenCalledWith(42)
  })

  it('loads tables when a schema is selected', async () => {
    renderAt('42')
    await screen.findByText('public')

    fireEvent.change(screen.getByTestId('schema-selector-select'), { target: { value: 'public' } })

    expect(await screen.findByText('users')).toBeInTheDocument()
    expect(listTablesMock).toHaveBeenCalledWith(42, 'public')
  })

  it('loads the table detail when a table row is selected', async () => {
    renderAt('42')
    await screen.findByText('public')
    fireEvent.change(screen.getByTestId('schema-selector-select'), { target: { value: 'public' } })
    await screen.findByText('users')

    fireEvent.click(screen.getByTestId('table-list-row'))

    expect(await screen.findByTestId('table-detail-panel')).toBeInTheDocument()
    expect(getTableDetailMock).toHaveBeenCalledWith(42, 'public', 'users')
  })
})
