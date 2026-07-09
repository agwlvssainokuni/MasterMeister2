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
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renders the message', () => {
    render(<ConfirmDialog message="Delete this record?" onConfirm={vi.fn()} onCancel={vi.fn()} />)

    expect(screen.getByText('Delete this record?')).toBeInTheDocument()
  })

  it('calls onConfirm when the confirm button is clicked', () => {
    const onConfirm = vi.fn()
    render(<ConfirmDialog message="Delete this record?" onConfirm={onConfirm} onCancel={vi.fn()} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the cancel button is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog message="Delete this record?" onConfirm={vi.fn()} onCancel={onCancel} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel-button'))

    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})