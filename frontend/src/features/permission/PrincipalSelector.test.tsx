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
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listGroups } from '../group/api'
import type { GroupSummary } from '../group/types'
import { PrincipalSelector } from './PrincipalSelector'

vi.mock('../group/api', () => ({
  listGroups: vi.fn(),
}))

const listGroupsMock = vi.mocked(listGroups)

const groups: GroupSummary[] = [{ id: 5, name: 'group-5', createdAt: '2026-01-01T00:00:00Z' }]

describe('PrincipalSelector', () => {
  beforeEach(() => {
    listGroupsMock.mockReset()
    listGroupsMock.mockResolvedValue(groups)
  })

  it('defaults to the USER tab and submits a USER principal by id', () => {
    const onSelect = vi.fn()
    render(<PrincipalSelector selected={null} onSelect={onSelect} />)

    fireEvent.change(screen.getByTestId('principal-selector-user-id-input'), { target: { value: '3' } })
    fireEvent.click(screen.getByTestId('principal-selector-user-select-button'))

    expect(onSelect).toHaveBeenCalledWith({ principalType: 'USER', principalId: 3 })
  })

  it('switches to the GROUP tab, lists groups, and submits a GROUP principal on select', async () => {
    const onSelect = vi.fn()
    render(<PrincipalSelector selected={null} onSelect={onSelect} />)

    fireEvent.click(screen.getByTestId('principal-selector-group-tab'))

    expect(await screen.findByText('group-5')).toBeInTheDocument()

    fireEvent.change(screen.getByTestId('principal-selector-group-select'), { target: { value: '5' } })

    expect(onSelect).toHaveBeenCalledWith({ principalType: 'GROUP', principalId: 5 })
  })

  it('does not list groups while the USER tab is active', () => {
    render(<PrincipalSelector selected={null} onSelect={vi.fn()} />)

    expect(listGroupsMock).not.toHaveBeenCalled()
  })
})