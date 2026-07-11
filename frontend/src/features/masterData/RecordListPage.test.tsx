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
import { applyChanges, listAccessibleTables, listRecords } from './api'
import { RecordListPage } from './RecordListPage'
import type { ColumnMetadata, MutationResult, RecordListResult, TableSummary } from './types'

vi.mock('./api', () => ({
  applyChanges: vi.fn(),
  listAccessibleTables: vi.fn(),
  listRecords: vi.fn(),
}))

const applyChangesMock = vi.mocked(applyChanges)
const listAccessibleTablesMock = vi.mocked(listAccessibleTables)
const listRecordsMock = vi.mocked(listRecords)

const columns: ColumnMetadata[] = [
  { columnName: 'id', dataType: 'INTEGER', nullable: false, primaryKeySequence: 1, effectivePermission: 'READ' },
  { columnName: 'name', dataType: 'VARCHAR', nullable: true, primaryKeySequence: null, effectivePermission: 'UPDATE' },
]

const listResult: RecordListResult = {
  columns,
  records: { content: [[1, 'alice']], totalCount: 1, page: 0, pageSize: 50 },
}

function tableSummary(overrides: Partial<TableSummary> = {}): TableSummary {
  return {
    schemaName: 'public',
    tableName: 'employees',
    tableType: 'TABLE',
    comment: null,
    effectivePermission: 'UPDATE',
    canCreate: false,
    canDelete: false,
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/master-data/1/public/employees']}>
      <Routes>
        <Route path="/master-data/:connectionId/:schema/:table" element={<RecordListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RecordListPage', () => {
  beforeEach(() => {
    applyChangesMock.mockReset()
    listAccessibleTablesMock.mockReset()
    listRecordsMock.mockReset()
    listAccessibleTablesMock.mockResolvedValue([tableSummary()])
    listRecordsMock.mockResolvedValue(listResult)
  })

  it('renders the record rows fetched via listRecords', async () => {
    renderPage()

    expect(await screen.findByDisplayValue('alice')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(listRecordsMock).toHaveBeenCalledWith(1, 'public', 'employees', expect.anything(), { page: 0, pageSize: 50 })
  })

  it('shows a read-only cell for READ-permission columns and an editable input for UPDATE-permission columns', async () => {
    renderPage()
    await screen.findByDisplayValue('alice')

    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('1')).not.toBeInTheDocument()
  })

  it('does not show the delete checkbox column when the table is not deletable', async () => {
    renderPage()
    await screen.findByDisplayValue('alice')

    expect(screen.queryByTestId('record-list-page-delete-checkbox')).not.toBeInTheDocument()
  })

  it('shows the delete checkbox column when the table is deletable', async () => {
    listAccessibleTablesMock.mockResolvedValue([tableSummary({ canDelete: true })])
    renderPage()

    expect(await screen.findByTestId('record-list-page-delete-checkbox')).toBeInTheDocument()
  })

  it('does not show the new-row UI when the table is not creatable', async () => {
    renderPage()
    await screen.findByDisplayValue('alice')

    expect(screen.queryByTestId('record-list-page-new-rows')).not.toBeInTheDocument()
  })

  it('shows the new-row UI when the table is creatable', async () => {
    listAccessibleTablesMock.mockResolvedValue([tableSummary({ canCreate: true })])
    renderPage()

    expect(await screen.findByTestId('record-list-page-new-rows')).toBeInTheDocument()
  })

  it('edits a cell and applies the change via applyChanges, reloading on success', async () => {
    const mutation: MutationResult = { success: true, createdCount: 0, updatedCount: 1, deletedCount: 0, errorMessage: null }
    applyChangesMock.mockResolvedValue(mutation)
    renderPage()
    await screen.findByDisplayValue('alice')

    fireEvent.change(screen.getByDisplayValue('alice'), { target: { value: 'bob' } })
    fireEvent.click(screen.getByTestId('record-list-page-apply'))

    expect(await screen.findByText('反映が完了しました')).toBeInTheDocument()
    expect(applyChangesMock).toHaveBeenCalledWith(1, 'public', 'employees', {
      creates: [],
      updates: [{ primaryKeyValues: { id: 1 }, changedValues: { name: 'bob' } }],
      deletes: [],
    })
    expect(listRecordsMock).toHaveBeenCalledTimes(2)
  })

  it('keeps pending changes when the mutation fails', async () => {
    const mutation: MutationResult = {
      success: false,
      createdCount: 0,
      updatedCount: 0,
      deletedCount: 0,
      errorMessage: '一意制約違反です',
    }
    applyChangesMock.mockResolvedValue(mutation)
    renderPage()
    await screen.findByDisplayValue('alice')

    fireEvent.change(screen.getByDisplayValue('alice'), { target: { value: 'bob' } })
    fireEvent.click(screen.getByTestId('record-list-page-apply'))

    expect(await screen.findByText('反映に失敗しました')).toBeInTheDocument()
    expect(screen.getByDisplayValue('bob')).toBeInTheDocument()
    expect(listRecordsMock).toHaveBeenCalledTimes(1)
  })

  it('toggles a row for deletion and applies deletes via applyChanges', async () => {
    listAccessibleTablesMock.mockResolvedValue([tableSummary({ canDelete: true })])
    const mutation: MutationResult = { success: true, createdCount: 0, updatedCount: 0, deletedCount: 1, errorMessage: null }
    applyChangesMock.mockResolvedValue(mutation)
    renderPage()
    await screen.findByTestId('record-list-page-delete-checkbox')

    fireEvent.click(screen.getByTestId('record-list-page-delete-checkbox'))
    fireEvent.click(screen.getByTestId('record-list-page-apply'))

    expect(await screen.findByText('反映が完了しました')).toBeInTheDocument()
    expect(applyChangesMock).toHaveBeenCalledWith(1, 'public', 'employees', {
      creates: [],
      updates: [],
      deletes: [{ primaryKeyValues: { id: 1 } }],
    })
  })

  it('adds a new row, edits its values, and applies creates via applyChanges', async () => {
    listAccessibleTablesMock.mockResolvedValue([tableSummary({ canCreate: true })])
    const mutation: MutationResult = { success: true, createdCount: 1, updatedCount: 0, deletedCount: 0, errorMessage: null }
    applyChangesMock.mockResolvedValue(mutation)
    renderPage()
    await screen.findByTestId('record-list-page-new-rows')

    fireEvent.click(screen.getByTestId('record-list-page-add-row'))
    const newRowInputs = screen.getAllByRole('textbox').filter((el) => (el as HTMLInputElement).value === '')
    fireEvent.change(newRowInputs[0], { target: { value: '2' } })
    fireEvent.change(newRowInputs[1], { target: { value: 'carol' } })
    fireEvent.click(screen.getByTestId('record-list-page-apply'))

    expect(await screen.findByText('反映が完了しました')).toBeInTheDocument()
    expect(applyChangesMock).toHaveBeenCalledWith(1, 'public', 'employees', {
      creates: [{ values: { id: '2', name: 'carol' } }],
      updates: [],
      deletes: [],
    })
  })

  it('disables the previous-page button on the first page and the next-page button on the last page', async () => {
    renderPage()
    await screen.findByDisplayValue('alice')

    expect(screen.getByText('前へ')).toBeDisabled()
    expect(screen.getByText('次へ')).toBeDisabled()
  })
})