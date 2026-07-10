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
import { createGroup, deleteGroup, listGroups, renameGroup } from './api'
import { GroupListPage } from './GroupListPage'
import type { GroupSummary } from './types'

vi.mock('./api', () => ({
  createGroup: vi.fn(),
  deleteGroup: vi.fn(),
  listGroups: vi.fn(),
  renameGroup: vi.fn(),
}))

const createGroupMock = vi.mocked(createGroup)
const deleteGroupMock = vi.mocked(deleteGroup)
const listGroupsMock = vi.mocked(listGroups)
const renameGroupMock = vi.mocked(renameGroup)

const group: GroupSummary = { id: 1, name: 'group-1', createdAt: '2026-01-01T00:00:00Z' }

function renderPage() {
  return render(
    <MemoryRouter>
      <GroupListPage />
    </MemoryRouter>,
  )
}

describe('GroupListPage', () => {
  beforeEach(() => {
    createGroupMock.mockReset()
    deleteGroupMock.mockReset()
    listGroupsMock.mockReset()
    renameGroupMock.mockReset()
    listGroupsMock.mockResolvedValue([group])
  })

  it('loads and displays groups on initial render', async () => {
    renderPage()

    expect(await screen.findByText('group-1')).toBeInTheDocument()
    expect(listGroupsMock).toHaveBeenCalledTimes(1)
  })

  it('creates a group and shows a success toast', async () => {
    createGroupMock.mockResolvedValue(2)
    renderPage()
    await screen.findByText('group-1')

    fireEvent.change(screen.getByTestId('group-list-page-new-name-input'), {
      target: { value: 'new-group' },
    })
    fireEvent.click(screen.getByTestId('group-list-page-new-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(createGroupMock).toHaveBeenCalledWith('new-group')
    expect(listGroupsMock).toHaveBeenCalledTimes(2)
  })

  it('shows an error toast when group creation fails', async () => {
    createGroupMock.mockRejectedValue(new Error('failed'))
    renderPage()
    await screen.findByText('group-1')

    fireEvent.change(screen.getByTestId('group-list-page-new-name-input'), {
      target: { value: 'new-group' },
    })
    fireEvent.click(screen.getByTestId('group-list-page-new-button'))

    expect(await screen.findByTestId('toast-notification-error')).toBeInTheDocument()
  })

  it('renames a group and shows a success toast', async () => {
    renameGroupMock.mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('group-1')

    fireEvent.click(screen.getByTestId('group-table-rename-button'))
    fireEvent.change(screen.getByTestId('group-table-rename-input'), {
      target: { value: 'renamed-group' },
    })
    fireEvent.click(screen.getByTestId('group-table-rename-commit-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(renameGroupMock).toHaveBeenCalledWith(1, 'renamed-group')
  })

  it('deletes a group and shows a success toast', async () => {
    deleteGroupMock.mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('group-1')

    fireEvent.click(screen.getByTestId('group-table-delete-button'))
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(await screen.findByTestId('toast-notification-success')).toBeInTheDocument()
    expect(deleteGroupMock).toHaveBeenCalledWith(1)
  })
})