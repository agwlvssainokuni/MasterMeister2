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
import { GroupMemberTable } from './GroupMemberTable'
import type { UserSummary } from './types'

const members: UserSummary[] = [
  { id: 1, email: 'user1@example.com' },
  { id: 2, email: 'user2@example.com' },
]

describe('GroupMemberTable', () => {
  it('renders a row for each member', () => {
    render(<GroupMemberTable members={members} onRemove={vi.fn()} />)

    expect(screen.getByText('user1@example.com')).toBeInTheDocument()
    expect(screen.getByText('user2@example.com')).toBeInTheDocument()
  })

  it('shows a confirmation dialog before calling onRemove, and does nothing on cancel', () => {
    const onRemove = vi.fn()
    render(<GroupMemberTable members={members} onRemove={onRemove} />)

    fireEvent.click(screen.getAllByTestId('group-member-table-remove-button')[0])
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel-button'))

    expect(onRemove).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onRemove with the user id when the confirmation dialog is confirmed', () => {
    const onRemove = vi.fn()
    render(<GroupMemberTable members={members} onRemove={onRemove} />)

    fireEvent.click(screen.getAllByTestId('group-member-table-remove-button')[1])
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(onRemove).toHaveBeenCalledWith(2)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})