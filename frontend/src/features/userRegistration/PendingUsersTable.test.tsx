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
import { describe, expect, it, vi } from 'vitest'
import { PendingUsersTable } from './PendingUsersTable'
import type { PendingUserSummary } from './types'

const users: PendingUserSummary[] = [
  { id: 1, email: 'user1@example.com', createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, email: 'user2@example.com', createdAt: '2026-01-02T00:00:00Z' },
]

describe('PendingUsersTable', () => {
  it('renders a row for each pending user', () => {
    render(<PendingUsersTable users={users} onApprove={vi.fn()} onReject={vi.fn()} />)

    expect(screen.getByText('user1@example.com')).toBeInTheDocument()
    expect(screen.getByText('user2@example.com')).toBeInTheDocument()
  })

  it('calls onApprove immediately without a confirmation dialog', () => {
    const onApprove = vi.fn()
    render(<PendingUsersTable users={users} onApprove={onApprove} onReject={vi.fn()} />)

    fireEvent.click(screen.getAllByTestId('pending-users-table-approve-button')[0])

    expect(onApprove).toHaveBeenCalledWith(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows a confirmation dialog before calling onReject, and does nothing on cancel', () => {
    const onReject = vi.fn()
    render(<PendingUsersTable users={users} onApprove={vi.fn()} onReject={onReject} />)

    fireEvent.click(screen.getAllByTestId('pending-users-table-reject-button')[0])
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel-button'))

    expect(onReject).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onReject when the confirmation dialog is confirmed', () => {
    const onReject = vi.fn()
    render(<PendingUsersTable users={users} onApprove={vi.fn()} onReject={onReject} />)

    fireEvent.click(screen.getAllByTestId('pending-users-table-reject-button')[0])
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(onReject).toHaveBeenCalledWith(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})