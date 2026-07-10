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
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { approveUser, listPendingUsers, rejectUser } from './api'
import { PendingUsersPage } from './PendingUsersPage'
import type { PendingUserSummary } from './types'

vi.mock('./api', () => ({
  listPendingUsers: vi.fn(),
  approveUser: vi.fn(),
  rejectUser: vi.fn(),
}))

const listPendingUsersMock = vi.mocked(listPendingUsers)
const approveUserMock = vi.mocked(approveUser)
const rejectUserMock = vi.mocked(rejectUser)

const user: PendingUserSummary = { id: 1, email: 'user@example.com', createdAt: '2026-01-01T00:00:00Z' }

describe('PendingUsersPage', () => {
  beforeEach(() => {
    listPendingUsersMock.mockReset()
    approveUserMock.mockReset()
    rejectUserMock.mockReset()
    listPendingUsersMock.mockResolvedValue([user])
  })

  it('loads and displays pending users on initial render', async () => {
    render(<PendingUsersPage />)

    expect(await screen.findByText('user@example.com')).toBeInTheDocument()
    expect(listPendingUsersMock).toHaveBeenCalledTimes(1)
  })

  it('approves a user and refetches the list on success', async () => {
    approveUserMock.mockResolvedValue(undefined)
    render(<PendingUsersPage />)
    await screen.findByText('user@example.com')

    fireEvent.click(screen.getByTestId('pending-users-table-approve-button'))

    await waitFor(() => expect(approveUserMock).toHaveBeenCalledWith(1))
    await waitFor(() => expect(listPendingUsersMock).toHaveBeenCalledTimes(2))
    expect(screen.getByTestId('toast-notification-success')).toBeInTheDocument()
  })

  it('rejects a user after confirmation and refetches the list on success', async () => {
    rejectUserMock.mockResolvedValue(undefined)
    render(<PendingUsersPage />)
    await screen.findByText('user@example.com')

    fireEvent.click(screen.getByTestId('pending-users-table-reject-button'))
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    await waitFor(() => expect(rejectUserMock).toHaveBeenCalledWith(1))
    await waitFor(() => expect(listPendingUsersMock).toHaveBeenCalledTimes(2))
    expect(screen.getByTestId('toast-notification-success')).toBeInTheDocument()
  })

  it('shows an error toast when approval fails', async () => {
    approveUserMock.mockRejectedValue(new Error('failed'))
    render(<PendingUsersPage />)
    await screen.findByText('user@example.com')

    fireEvent.click(screen.getByTestId('pending-users-table-approve-button'))

    expect(await screen.findByTestId('toast-notification-error')).toBeInTheDocument()
  })
})