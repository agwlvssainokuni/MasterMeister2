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

import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listAccessibleConnections } from '../features/rdbmsConnection/api'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import { AppRouter } from './AppRouter'

vi.mock('../features/rdbmsConnection/api', () => ({
  listAccessibleConnections: vi.fn(),
}))

const listAccessibleConnectionsMock = vi.mocked(listAccessibleConnections)

function renderAt(path: string) {
  window.history.pushState({}, '', path)
  return render(<AppRouter />)
}

describe('AppRouter', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
    useConnectionStore.setState({ connectionId: null, connections: [] })
    listAccessibleConnectionsMock.mockReset()
    listAccessibleConnectionsMock.mockResolvedValue([])
  })

  it('renders the login page without the AppLayout navigation on a public route', () => {
    renderAt('/login')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav')).not.toBeInTheDocument()
  })

  it('renders the registration request page without the AppLayout navigation on a public route', () => {
    renderAt('/register')

    expect(screen.getByTestId('registration-request-page')).toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav')).not.toBeInTheDocument()
  })

  it('redirects to /login when accessing a protected route while unauthenticated', () => {
    renderAt('/admin/audit-logs')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('redirects to /login when accessing /admin/rdbms-connections while unauthenticated', () => {
    renderAt('/admin/rdbms-connections')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('redirects to /login when accessing /admin/schema/:connectionId while unauthenticated', () => {
    renderAt('/admin/schema/1')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('redirects to /login when accessing /admin/groups while unauthenticated', () => {
    renderAt('/admin/groups')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('redirects to /login when accessing /admin/groups/:id while unauthenticated', () => {
    renderAt('/admin/groups/1')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('redirects to /login when accessing /admin/permissions while unauthenticated', () => {
    renderAt('/admin/permissions')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('renders the AppLayout navigation for authenticated routes', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAt('/')

    expect(screen.getByTestId('app-layout-nav')).toBeInTheDocument()
  })
})