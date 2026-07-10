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
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTableDetail, listSchemas, listTables } from '../schema/api'
import type { ColumnDetail, TableDetail, TableMetadata } from '../schema/types'
import { PermissionTree } from './PermissionTree'
import type { SchemaTreeNode } from './types'

vi.mock('../schema/api', () => ({
  listSchemas: vi.fn(),
  listTables: vi.fn(),
  getTableDetail: vi.fn(),
}))

const listSchemasMock = vi.mocked(listSchemas)
const listTablesMock = vi.mocked(listTables)
const getTableDetailMock = vi.mocked(getTableDetail)

const table: TableMetadata = { schemaName: 'public', tableName: 'employees', tableType: 'TABLE', comment: null }
const column: ColumnDetail = {
  columnName: 'id',
  dataType: 'INTEGER',
  nullable: false,
  comment: null,
  ordinalPosition: 1,
  primaryKeySequence: 1,
}
const tableDetail: TableDetail = { ...table, columns: [column] }

function renderTree(selectedNode: SchemaTreeNode | null = null, onSelectNode = vi.fn()) {
  return render(<PermissionTree connectionId={1} selectedNode={selectedNode} onSelectNode={onSelectNode} />)
}

describe('PermissionTree', () => {
  beforeEach(() => {
    listSchemasMock.mockReset()
    listTablesMock.mockReset()
    getTableDetailMock.mockReset()
    listSchemasMock.mockResolvedValue(['public'])
    listTablesMock.mockResolvedValue([table])
    getTableDetailMock.mockResolvedValue(tableDetail)
  })

  it('loads schemas eagerly on mount', async () => {
    renderTree()

    expect(await screen.findByText('public')).toBeInTheDocument()
    expect(listSchemasMock).toHaveBeenCalledWith(1)
    expect(listTablesMock).not.toHaveBeenCalled()
  })

  it('calls onSelectNode with a schema-level node when the schema label is clicked', async () => {
    const onSelectNode = vi.fn()
    renderTree(null, onSelectNode)
    await screen.findByText('public')

    fireEvent.click(screen.getByTestId('permission-tree-schema-select'))

    expect(onSelectNode).toHaveBeenCalledWith({ level: 'schema', schema: 'public' })
  })

  it('lazily loads tables only when the schema is expanded', async () => {
    renderTree()
    await screen.findByText('public')

    fireEvent.click(screen.getByTestId('permission-tree-schema-toggle'))

    expect(await screen.findByText('employees')).toBeInTheDocument()
    expect(listTablesMock).toHaveBeenCalledWith(1, 'public')
  })

  it('lazily loads columns only when the table is expanded', async () => {
    renderTree()
    await screen.findByText('public')
    fireEvent.click(screen.getByTestId('permission-tree-schema-toggle'))
    await screen.findByText('employees')

    fireEvent.click(screen.getByTestId('permission-tree-table-toggle'))

    expect(await screen.findByText('id')).toBeInTheDocument()
    expect(getTableDetailMock).toHaveBeenCalledWith(1, 'public', 'employees')
  })

  it('calls onSelectNode with a column-level node when a column label is clicked', async () => {
    const onSelectNode = vi.fn()
    renderTree(null, onSelectNode)
    await screen.findByText('public')
    fireEvent.click(screen.getByTestId('permission-tree-schema-toggle'))
    await screen.findByText('employees')
    fireEvent.click(screen.getByTestId('permission-tree-table-toggle'))
    await screen.findByText('id')

    fireEvent.click(screen.getByTestId('permission-tree-column-select'))

    expect(onSelectNode).toHaveBeenCalledWith({ level: 'column', schema: 'public', table: 'employees', column: 'id' })
  })
})