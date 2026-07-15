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

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listAccessibleConnections } from '../features/rdbmsConnection/api'
import type { ConnectionSummary } from '../features/rdbmsConnection/types'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import { AppLayout } from './AppLayout'

vi.mock('../features/rdbmsConnection/api', () => ({
  listAccessibleConnections: vi.fn(),
}))

const listAccessibleConnectionsMock = vi.mocked(listAccessibleConnections)

const connections: ConnectionSummary[] = [
  { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'localhost', databaseName: 'db1' },
  { id: 2, name: 'conn-2', rdbmsType: 'POSTGRESQL', host: 'localhost', databaseName: 'db2' },
]

function renderAtPath(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/master-data/:connectionId/:schema/:table"
          element={
            <AppLayout>
              <div data-testid="detail-page" />
            </AppLayout>
          }
        />
        <Route
          path="/master-data"
          element={
            <AppLayout>
              <div data-testid="list-page" />
            </AppLayout>
          }
        />
        <Route
          path="/query-builder"
          element={
            <AppLayout>
              <div data-testid="query-builder-page" />
            </AppLayout>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
    useConnectionStore.setState({ connectionId: null, connections: [] })
    listAccessibleConnectionsMock.mockReset()
    listAccessibleConnectionsMock.mockResolvedValue(connections)
  })

  it('shows the pending-users and audit-logs links for an authenticated admin', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'admin@example.com', role: 'ADMIN' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAtPath('/query-builder')

    expect(screen.getByTestId('app-layout-nav-pending-users')).toHaveAttribute(
      'href',
      '/admin/pending-users',
    )
    expect(screen.getByTestId('app-layout-nav-audit-logs')).toHaveAttribute(
      'href',
      '/admin/audit-logs',
    )
    expect(screen.getByTestId('app-layout-nav-rdbms-connections')).toHaveAttribute(
      'href',
      '/admin/rdbms-connections',
    )
    expect(screen.getByTestId('app-layout-nav-groups')).toHaveAttribute('href', '/admin/groups')
    expect(screen.getByTestId('app-layout-nav-permissions')).toHaveAttribute(
      'href',
      '/admin/permissions',
    )
  })

  it('hides the admin-only links for an authenticated non-admin user', () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAtPath('/query-builder')

    expect(screen.queryByTestId('app-layout-nav-pending-users')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-audit-logs')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-rdbms-connections')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-groups')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-permissions')).not.toBeInTheDocument()
  })

  it('hides the admin-only links when unauthenticated', () => {
    renderAtPath('/query-builder')

    expect(screen.queryByTestId('app-layout-nav-pending-users')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-audit-logs')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-rdbms-connections')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-groups')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-permissions')).not.toBeInTheDocument()
  })

  it('does not show the connection selector when unauthenticated', () => {
    renderAtPath('/query-builder')

    expect(screen.queryByTestId('app-layout-connection-select')).not.toBeInTheDocument()
    expect(listAccessibleConnectionsMock).not.toHaveBeenCalled()
  })

  it('fetches and displays accessible connections on mount for an authenticated user', async () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAtPath('/query-builder')

    expect(listAccessibleConnectionsMock).toHaveBeenCalled()
    expect(await screen.findByText('conn-1')).toBeInTheDocument()
    expect(screen.getByText('conn-2')).toBeInTheDocument()
  })

  it('updates connectionStore when a connection is selected', async () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAtPath('/query-builder')
    await screen.findByText('conn-1')

    fireEvent.change(screen.getByTestId('app-layout-connection-select'), { target: { value: '2' } })

    expect(useConnectionStore.getState().connectionId).toBe(2)
  })

  it('navigates to /master-data when switching connections while on the record detail route', async () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    render(
      <MemoryRouter initialEntries={['/master-data/1/public/customers']}>
        <Routes>
          <Route
            path="/master-data/:connectionId/:schema/:table"
            element={
              <AppLayout>
                <div data-testid="detail-page" />
              </AppLayout>
            }
          />
          <Route
            path="/master-data"
            element={
              <AppLayout>
                <div data-testid="list-page" />
              </AppLayout>
            }
          />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('conn-1')
    expect(screen.getByTestId('detail-page')).toBeInTheDocument()

    fireEvent.change(screen.getByTestId('app-layout-connection-select'), { target: { value: '2' } })

    await waitFor(() => expect(screen.getByTestId('list-page')).toBeInTheDocument())
  })

  it('does not navigate when switching connections on an unrelated route', async () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAtPath('/query-builder')
    await screen.findByText('conn-1')

    fireEvent.change(screen.getByTestId('app-layout-connection-select'), { target: { value: '2' } })

    expect(screen.getByTestId('query-builder-page')).toBeInTheDocument()
  })

  it('does not navigate on initial mount even when a connection is already restored on the detail route', () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })
    useConnectionStore.setState({ connectionId: 1, connections })

    render(
      <MemoryRouter initialEntries={['/master-data/1/public/customers']}>
        <Routes>
          <Route
            path="/master-data/:connectionId/:schema/:table"
            element={
              <AppLayout>
                <div data-testid="detail-page" />
              </AppLayout>
            }
          />
          <Route
            path="/master-data"
            element={
              <AppLayout>
                <div data-testid="list-page" />
              </AppLayout>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('detail-page')).toBeInTheDocument()
  })

  it('clears the connectionStore on logout', async () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })
    useConnectionStore.setState({ connectionId: 1, connections })

    renderAtPath('/query-builder')

    fireEvent.click(screen.getByTestId('app-layout-nav-logout'))

    expect(useConnectionStore.getState().connectionId).toBeNull()
    expect(useConnectionStore.getState().connections).toEqual([])
  })
})
