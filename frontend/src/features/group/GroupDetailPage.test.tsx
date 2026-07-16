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
import { listApprovedUsers } from '../userRegistration/api'
import type { UserAccountSummary } from '../userRegistration/types'
import { addUserToGroup, listGroupMembers, listGroups, removeUserFromGroup } from './api'
import { GroupDetailPage } from './GroupDetailPage'
import type { GroupSummary, UserSummary } from './types'

vi.mock('./api', () => ({
  addUserToGroup: vi.fn(),
  listGroupMembers: vi.fn(),
  listGroups: vi.fn(),
  removeUserFromGroup: vi.fn(),
}))
vi.mock('../userRegistration/api', () => ({
  listApprovedUsers: vi.fn(),
}))

const addUserToGroupMock = vi.mocked(addUserToGroup)
const listGroupMembersMock = vi.mocked(listGroupMembers)
const listGroupsMock = vi.mocked(listGroups)
const removeUserFromGroupMock = vi.mocked(removeUserFromGroup)
const listApprovedUsersMock = vi.mocked(listApprovedUsers)

const group: GroupSummary = { id: 1, name: 'group-1', createdAt: '2026-01-01T00:00:00Z' }
const member: UserSummary = { id: 7, email: 'member@example.com' }
const approvedUser: UserAccountSummary = { id: 9, email: 'addable@example.com' }

function renderPage(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/admin/groups/${id}`]}>
      <Routes>
        <Route path="/admin/groups/:id" element={<GroupDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('GroupDetailPage', () => {
  beforeEach(() => {
    addUserToGroupMock.mockReset()
    listGroupMembersMock.mockReset()
    listGroupsMock.mockReset()
    removeUserFromGroupMock.mockReset()
    listApprovedUsersMock.mockReset()
    listGroupsMock.mockResolvedValue([group])
    listGroupMembersMock.mockResolvedValue([member])
    listApprovedUsersMock.mockResolvedValue([approvedUser])
  })

  it('loads the group name (filtered client-side from listGroups) and its members', async () => {
    renderPage()

    expect(await screen.findByText('グループ「group-1」')).toBeInTheDocument()
    expect(screen.getByText('member@example.com')).toBeInTheDocument()
    expect(listGroupsMock).toHaveBeenCalledTimes(1)
    expect(listGroupMembersMock).toHaveBeenCalledWith(1)
  })

  it('adds a user selected from the approved-users dropdown and shows a success toast', async () => {
    addUserToGroupMock.mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('グループ「group-1」')

    expect(await screen.findByText('addable@example.com')).toBeInTheDocument()
    fireEvent.change(screen.getByTestId('group-detail-page-new-user-select'), {
      target: { value: '9' },
    })
    fireEvent.click(screen.getByTestId('group-detail-page-add-user-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(addUserToGroupMock).toHaveBeenCalledWith(1, 9)
  })

  it('excludes users who are already members from the addable-users dropdown', async () => {
    listApprovedUsersMock.mockResolvedValue([approvedUser, { id: 7, email: 'member@example.com' }])
    renderPage()
    await screen.findByText('グループ「group-1」')

    await screen.findByText('addable@example.com')
    expect(
      screen.getByTestId('group-detail-page-new-user-select').querySelector('option[value="7"]'),
    ).not.toBeInTheDocument()
  })

  it('shows an error toast when adding a user fails', async () => {
    addUserToGroupMock.mockRejectedValue(new Error('failed'))
    renderPage()
    await screen.findByText('グループ「group-1」')

    fireEvent.change(screen.getByTestId('group-detail-page-new-user-select'), {
      target: { value: '9' },
    })
    fireEvent.click(screen.getByTestId('group-detail-page-add-user-button'))

    expect(await screen.findByTestId('toast-notification-error')).toBeInTheDocument()
  })

  it('removes a member and shows a success toast', async () => {
    removeUserFromGroupMock.mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('member@example.com')

    fireEvent.click(screen.getByTestId('group-member-table-remove-button'))
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(removeUserFromGroupMock).toHaveBeenCalledWith(1, 7)
  })
})