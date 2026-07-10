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
import { listConnections } from '../rdbmsConnection/api'
import type { ConnectionSummary } from '../rdbmsConnection/types'
import { ConnectionSelector } from './ConnectionSelector'

vi.mock('../rdbmsConnection/api', () => ({
  listConnections: vi.fn(),
}))

const listConnectionsMock = vi.mocked(listConnections)

const connections: ConnectionSummary[] = [
  { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'host1', databaseName: 'db1' },
  { id: 2, name: 'conn-2', rdbmsType: 'POSTGRESQL', host: 'host2', databaseName: 'db2' },
]

describe('ConnectionSelector', () => {
  beforeEach(() => {
    listConnectionsMock.mockReset()
    listConnectionsMock.mockResolvedValue(connections)
  })

  it('loads and lists connections as options', async () => {
    render(<ConnectionSelector selected={null} onSelect={vi.fn()} />)

    expect(await screen.findByText('conn-1')).toBeInTheDocument()
    expect(screen.getByText('conn-2')).toBeInTheDocument()
  })

  it('calls onSelect with the chosen connection id', async () => {
    const onSelect = vi.fn()
    render(<ConnectionSelector selected={null} onSelect={onSelect} />)
    await screen.findByText('conn-1')

    fireEvent.change(screen.getByTestId('connection-selector-select'), { target: { value: '2' } })

    expect(onSelect).toHaveBeenCalledWith(2)
  })
})