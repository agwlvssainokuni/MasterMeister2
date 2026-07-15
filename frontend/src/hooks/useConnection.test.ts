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

import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import type { ConnectionSummary } from '../features/rdbmsConnection/types'
import { useConnectionStore } from '../store/connectionStore'
import { useConnection } from './useConnection'

const connections: ConnectionSummary[] = [
  { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'localhost', databaseName: 'db1' },
]

describe('useConnection', () => {
  beforeEach(() => {
    useConnectionStore.setState({ connectionId: null, connections: [] })
  })

  it('starts with no connectionId and an empty connections list', () => {
    const { result } = renderHook(() => useConnection())

    expect(result.current.connectionId).toBeNull()
    expect(result.current.connections).toEqual([])
  })

  it('reflects setConnections and setConnectionId', () => {
    const { result } = renderHook(() => useConnection())

    act(() => {
      result.current.setConnections(connections)
      result.current.setConnectionId(1)
    })

    expect(result.current.connections).toEqual(connections)
    expect(result.current.connectionId).toBe(1)
  })

  it('clearConnection resets connectionId and connections', () => {
    const { result } = renderHook(() => useConnection())
    act(() => {
      result.current.setConnections(connections)
      result.current.setConnectionId(1)
    })

    act(() => {
      result.current.clearConnection()
    })

    expect(result.current.connectionId).toBeNull()
    expect(result.current.connections).toEqual([])
  })
})
