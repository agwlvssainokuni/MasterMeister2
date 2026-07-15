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

import { beforeEach, describe, expect, it } from 'vitest'
import type { ConnectionSummary } from '../features/rdbmsConnection/types'
import { useConnectionStore } from './connectionStore'

const connections: ConnectionSummary[] = [
  { id: 1, name: 'conn-1', rdbmsType: 'MYSQL', host: 'localhost', databaseName: 'db1' },
]

describe('connectionStore', () => {
  beforeEach(() => {
    useConnectionStore.setState({ connectionId: null, connections: [] })
    sessionStorage.clear()
  })

  it('starts with no connectionId and an empty connections list', () => {
    const state = useConnectionStore.getState()
    expect(state.connectionId).toBeNull()
    expect(state.connections).toEqual([])
  })

  it('setConnections sets the connections list', () => {
    useConnectionStore.getState().setConnections(connections)

    expect(useConnectionStore.getState().connections).toEqual(connections)
  })

  it('setConnectionId sets the connectionId', () => {
    useConnectionStore.getState().setConnectionId(1)

    expect(useConnectionStore.getState().connectionId).toBe(1)
  })

  it('clearConnection resets connectionId and connections', () => {
    useConnectionStore.getState().setConnections(connections)
    useConnectionStore.getState().setConnectionId(1)

    useConnectionStore.getState().clearConnection()

    const state = useConnectionStore.getState()
    expect(state.connectionId).toBeNull()
    expect(state.connections).toEqual([])
  })

  it('persists state to sessionStorage on setConnectionId', () => {
    useConnectionStore.getState().setConnectionId(1)

    const stored = JSON.parse(sessionStorage.getItem('connection-storage') ?? '{}')
    expect(stored.state.connectionId).toBe(1)
  })

  it('clears the persisted sessionStorage state on clearConnection', () => {
    useConnectionStore.getState().setConnectionId(1)

    useConnectionStore.getState().clearConnection()

    const stored = JSON.parse(sessionStorage.getItem('connection-storage') ?? '{}')
    expect(stored.state.connectionId).toBeNull()
  })
})