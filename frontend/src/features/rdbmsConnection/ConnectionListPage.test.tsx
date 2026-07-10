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
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listConnections, testConnection } from './api/connectionApi'
import { ConnectionListPage } from './ConnectionListPage'
import type { ConnectionSummary } from './types'

vi.mock('./api/connectionApi', () => ({
  listConnections: vi.fn(),
  testConnection: vi.fn(),
}))

const listConnectionsMock = vi.mocked(listConnections)
const testConnectionMock = vi.mocked(testConnection)

const connection: ConnectionSummary = { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'host1', databaseName: 'db1' }

function renderPage() {
  return render(
    <MemoryRouter>
      <ConnectionListPage />
    </MemoryRouter>,
  )
}

describe('ConnectionListPage', () => {
  beforeEach(() => {
    listConnectionsMock.mockReset()
    testConnectionMock.mockReset()
    listConnectionsMock.mockResolvedValue([connection])
  })

  it('loads and displays connections on initial render', async () => {
    renderPage()

    expect(await screen.findByText('conn-1')).toBeInTheDocument()
    expect(listConnectionsMock).toHaveBeenCalledTimes(1)
  })

  it('links the new-registration button to the creation route', async () => {
    renderPage()
    await screen.findByText('conn-1')

    expect(screen.getByTestId('connection-list-page-new-button')).toHaveAttribute(
      'href',
      '/admin/rdbms-connections/new',
    )
  })

  it('shows a success toast when the connection test succeeds', async () => {
    testConnectionMock.mockResolvedValue({ success: true, message: 'ok' })
    renderPage()
    await screen.findByText('conn-1')

    fireEvent.click(screen.getByTestId('connection-table-test-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(testConnectionMock).toHaveBeenCalledWith(1)
  })

  it('shows an error toast when the connection test fails', async () => {
    testConnectionMock.mockResolvedValue({ success: false, message: 'timeout' })
    renderPage()
    await screen.findByText('conn-1')

    fireEvent.click(screen.getByTestId('connection-table-test-button'))

    expect(await screen.findByTestId('toast-notification-error')).toBeInTheDocument()
  })

  it('opens the schema import panel for the selected connection when import-schema is clicked', async () => {
    renderPage()
    await screen.findByText('conn-1')

    fireEvent.click(screen.getByTestId('connection-table-import-schema-button'))

    expect(screen.getByTestId('schema-import-panel')).toBeInTheDocument()
  })

  it('closes the schema import panel when its close button is clicked', async () => {
    renderPage()
    await screen.findByText('conn-1')

    fireEvent.click(screen.getByTestId('connection-table-import-schema-button'))
    fireEvent.click(screen.getByTestId('schema-import-panel-close-button'))

    expect(screen.queryByTestId('schema-import-panel')).not.toBeInTheDocument()
  })
})