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
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { AppLayout } from './AppLayout'

describe('AppLayout', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
  })

  it('shows the pending-users and audit-logs links for an authenticated admin', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'admin@example.com', role: 'ADMIN' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    render(<AppLayout>content</AppLayout>)

    expect(screen.getByTestId('app-layout-nav-pending-users')).toHaveAttribute(
      'href',
      '/admin/pending-users',
    )
    expect(screen.getByTestId('app-layout-nav-audit-logs')).toHaveAttribute(
      'href',
      '/admin/audit-logs',
    )
  })

  it('hides the admin-only links for an authenticated non-admin user', () => {
    useAuthStore.setState({
      currentUser: { id: 2, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    render(<AppLayout>content</AppLayout>)

    expect(screen.queryByTestId('app-layout-nav-pending-users')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-audit-logs')).not.toBeInTheDocument()
  })

  it('hides the admin-only links when unauthenticated', () => {
    render(<AppLayout>content</AppLayout>)

    expect(screen.queryByTestId('app-layout-nav-pending-users')).not.toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav-audit-logs')).not.toBeInTheDocument()
  })
})