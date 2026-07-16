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
import { listGroups } from '../group/api'
import type { ConnectionSummary } from '../rdbmsConnection/types'
import { listConnections } from '../rdbmsConnection/api'
import { getTableDetail, listSchemas, listTables } from '../schema/api'
import { listApprovedUsers } from '../userRegistration/api'
import { PermissionAssignmentPage } from './PermissionAssignmentPage'

vi.mock('../rdbmsConnection/api', () => ({
  listConnections: vi.fn(),
}))
vi.mock('../group/api', () => ({
  listGroups: vi.fn(),
}))
vi.mock('../schema/api', () => ({
  listSchemas: vi.fn(),
  listTables: vi.fn(),
  getTableDetail: vi.fn(),
}))
vi.mock('../userRegistration/api', () => ({
  listApprovedUsers: vi.fn(),
}))

const listConnectionsMock = vi.mocked(listConnections)
const listGroupsMock = vi.mocked(listGroups)
const listSchemasMock = vi.mocked(listSchemas)
const listTablesMock = vi.mocked(listTables)
const getTableDetailMock = vi.mocked(getTableDetail)
const listApprovedUsersMock = vi.mocked(listApprovedUsers)

const connection: ConnectionSummary = { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'host1', databaseName: 'db1' }

describe('PermissionAssignmentPage', () => {
  beforeEach(() => {
    listConnectionsMock.mockReset()
    listGroupsMock.mockReset()
    listSchemasMock.mockReset()
    listTablesMock.mockReset()
    getTableDetailMock.mockReset()
    listApprovedUsersMock.mockReset()
    listConnectionsMock.mockResolvedValue([connection])
    listGroupsMock.mockResolvedValue([])
    listApprovedUsersMock.mockResolvedValue([{ id: 7, email: 'user-7@example.com' }])
    listSchemasMock.mockResolvedValue(['public'])
    listTablesMock.mockResolvedValue([])
    getTableDetailMock.mockResolvedValue({
      schemaName: 'public',
      tableName: 'employees',
      tableType: 'TABLE',
      comment: null,
      columns: [],
    })
  })

  it('shows the tree and YAML panel once a connection is selected, but not the form without a principal/node', async () => {
    render(<PermissionAssignmentPage />)
    await screen.findByText('conn-1')

    fireEvent.change(screen.getByTestId('connection-selector-select'), { target: { value: '1' } })

    expect(await screen.findByTestId('permission-tree')).toBeInTheDocument()
    expect(screen.getByTestId('permission-yaml-panel')).toBeInTheDocument()
    expect(screen.queryByTestId('permission-form')).not.toBeInTheDocument()
  })

  it('shows the form once both a principal and a tree node are selected', async () => {
    render(<PermissionAssignmentPage />)
    await screen.findByText('conn-1')
    fireEvent.change(screen.getByTestId('connection-selector-select'), { target: { value: '1' } })
    await screen.findByTestId('permission-tree')
    await screen.findByText('public')

    await screen.findByText('user-7@example.com')
    fireEvent.change(screen.getByTestId('principal-selector-user-select'), { target: { value: '7' } })
    fireEvent.click(screen.getByTestId('permission-tree-schema-select'))

    expect(await screen.findByTestId('permission-form')).toBeInTheDocument()
  })

  it('hides the form again when switching to a different connection', async () => {
    listConnectionsMock.mockResolvedValue([
      connection,
      { id: 2, name: 'conn-2', rdbmsType: 'POSTGRESQL', host: 'host2', databaseName: 'db2' },
    ])
    render(<PermissionAssignmentPage />)
    await screen.findByText('conn-1')
    fireEvent.change(screen.getByTestId('connection-selector-select'), { target: { value: '1' } })
    await screen.findByTestId('permission-tree')
    await screen.findByText('public')
    await screen.findByText('user-7@example.com')
    fireEvent.change(screen.getByTestId('principal-selector-user-select'), { target: { value: '7' } })
    fireEvent.click(screen.getByTestId('permission-tree-schema-select'))
    await screen.findByTestId('permission-form')

    fireEvent.change(screen.getByTestId('connection-selector-select'), { target: { value: '2' } })

    expect(screen.queryByTestId('permission-form')).not.toBeInTheDocument()
  })
})