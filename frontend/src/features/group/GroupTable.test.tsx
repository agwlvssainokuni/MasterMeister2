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
import { GroupTable } from './GroupTable'
import type { GroupSummary } from './types'

const groups: GroupSummary[] = [
  { id: 1, name: 'group-1', createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'group-2', createdAt: '2026-01-02T00:00:00Z' },
]

function renderTable(onOpenDetail = vi.fn(), onRename = vi.fn(), onDelete = vi.fn()) {
  return render(<GroupTable groups={groups} onOpenDetail={onOpenDetail} onRename={onRename} onDelete={onDelete} />)
}

describe('GroupTable', () => {
  it('renders a row for each group', () => {
    renderTable()

    expect(screen.getByText('group-1')).toBeInTheDocument()
    expect(screen.getByText('group-2')).toBeInTheDocument()
  })

  it('calls onOpenDetail with the group id when the detail button is clicked', () => {
    const onOpenDetail = vi.fn()
    renderTable(onOpenDetail)

    fireEvent.click(screen.getAllByTestId('group-table-detail-button')[1])

    expect(onOpenDetail).toHaveBeenCalledWith(2)
  })

  it('switches a row into rename mode and commits the new name', () => {
    const onRename = vi.fn()
    renderTable(vi.fn(), onRename)

    fireEvent.click(screen.getAllByTestId('group-table-rename-button')[0])
    const input = screen.getByTestId('group-table-rename-input')
    fireEvent.change(input, { target: { value: 'renamed-group' } })
    fireEvent.click(screen.getByTestId('group-table-rename-commit-button'))

    expect(onRename).toHaveBeenCalledWith(1, 'renamed-group')
    expect(screen.queryByTestId('group-table-rename-input')).not.toBeInTheDocument()
  })

  it('cancels rename mode without calling onRename', () => {
    const onRename = vi.fn()
    renderTable(vi.fn(), onRename)

    fireEvent.click(screen.getAllByTestId('group-table-rename-button')[0])
    fireEvent.click(screen.getByTestId('group-table-rename-cancel-button'))

    expect(onRename).not.toHaveBeenCalled()
    expect(screen.queryByTestId('group-table-rename-input')).not.toBeInTheDocument()
  })

  it('shows a confirmation dialog before calling onDelete, and does nothing on cancel', () => {
    const onDelete = vi.fn()
    renderTable(vi.fn(), vi.fn(), onDelete)

    fireEvent.click(screen.getAllByTestId('group-table-delete-button')[0])
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel-button'))

    expect(onDelete).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onDelete with the group id when the confirmation dialog is confirmed', () => {
    const onDelete = vi.fn()
    renderTable(vi.fn(), vi.fn(), onDelete)

    fireEvent.click(screen.getAllByTestId('group-table-delete-button')[1])
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(onDelete).toHaveBeenCalledWith(2)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})