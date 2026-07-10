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
import { createConnection, getConnection, testConnection, updateConnection } from './api'
import { ConnectionFormPage } from './ConnectionFormPage'
import type { ConnectionDetail } from './types'

vi.mock('./api', () => ({
  createConnection: vi.fn(),
  updateConnection: vi.fn(),
  getConnection: vi.fn(),
  testConnection: vi.fn(),
}))

const createConnectionMock = vi.mocked(createConnection)
const updateConnectionMock = vi.mocked(updateConnection)
const getConnectionMock = vi.mocked(getConnection)
const testConnectionMock = vi.mocked(testConnection)

const detail: ConnectionDetail = {
  id: 42,
  name: 'existing-connection',
  rdbmsType: 'POSTGRESQL',
  host: 'existing-host',
  port: 5432,
  databaseName: 'existing-db',
  username: 'existing-user',
  additionalParams: null,
}

function renderCreate() {
  return render(
    <MemoryRouter initialEntries={['/admin/rdbms-connections/new']}>
      <Routes>
        <Route path="/admin/rdbms-connections/new" element={<ConnectionFormPage mode="create" />} />
        <Route path="/admin/rdbms-connections" element={<div data-testid="connection-list-page-stub" />} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderEdit(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/admin/rdbms-connections/${id}`]}>
      <Routes>
        <Route path="/admin/rdbms-connections/:id" element={<ConnectionFormPage mode="edit" />} />
        <Route path="/admin/rdbms-connections" element={<div data-testid="connection-list-page-stub" />} />
      </Routes>
    </MemoryRouter>,
  )
}

function fillRequiredFields() {
  fireEvent.change(screen.getByTestId('connection-form-page-name-input'), { target: { value: 'new-connection' } })
  fireEvent.change(screen.getByTestId('connection-form-page-host-input'), { target: { value: 'new-host' } })
  fireEvent.change(screen.getByTestId('connection-form-page-database-name-input'), { target: { value: 'new-db' } })
  fireEvent.change(screen.getByTestId('connection-form-page-username-input'), { target: { value: 'new-user' } })
  fireEvent.change(screen.getByTestId('connection-form-page-password-input'), { target: { value: 'new-password' } })
}

describe('ConnectionFormPage', () => {
  beforeEach(() => {
    createConnectionMock.mockReset()
    updateConnectionMock.mockReset()
    getConnectionMock.mockReset()
    testConnectionMock.mockReset()
    getConnectionMock.mockResolvedValue(detail)
  })

  it('creates a new connection and navigates back to the list on success', async () => {
    createConnectionMock.mockResolvedValue(1)
    renderCreate()

    fillRequiredFields()
    fireEvent.click(screen.getByTestId('connection-form-page-submit-button'))

    expect(await screen.findByTestId('connection-list-page-stub')).toBeInTheDocument()
    expect(createConnectionMock).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'new-connection', host: 'new-host', password: 'new-password' }),
    )
  })

  it('loads the existing connection in edit mode and leaves the password field blank', async () => {
    renderEdit('42')

    expect(await screen.findByTestId('connection-form-page-name-input')).toHaveValue('existing-connection')
    expect(getConnectionMock).toHaveBeenCalledWith(42)
    expect(screen.getByTestId('connection-form-page-password-input')).toHaveValue('')
  })

  it('saves the connection with an empty password when the password field is left blank', async () => {
    updateConnectionMock.mockResolvedValue(undefined)
    renderEdit('42')
    await screen.findByTestId('connection-form-page-name-input')

    fireEvent.click(screen.getByTestId('connection-form-page-submit-button'))

    expect(await screen.findByTestId('connection-list-page-stub')).toBeInTheDocument()
    expect(updateConnectionMock).toHaveBeenCalledWith(42, expect.objectContaining({ password: '' }))
  })

  it('shows a success message when the connection test succeeds', async () => {
    testConnectionMock.mockResolvedValue({ success: true, message: 'ok' })
    renderCreate()
    fillRequiredFields()

    fireEvent.click(screen.getByTestId('connection-form-page-test-button'))

    expect(await screen.findByTestId('connection-form-page-test-result-message')).toHaveTextContent('接続に成功しました')
  })

  it('shows a failure message with the reason when the connection test fails', async () => {
    testConnectionMock.mockResolvedValue({ success: false, message: 'timeout' })
    renderCreate()
    fillRequiredFields()

    fireEvent.click(screen.getByTestId('connection-form-page-test-button'))

    expect(await screen.findByTestId('connection-form-page-test-result-message')).toHaveTextContent(
      '接続に失敗しました: timeout',
    )
  })
})