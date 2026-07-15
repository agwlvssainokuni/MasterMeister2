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

import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createConnection,
  getConnection,
  listAccessibleConnections,
  listConnections,
  testConnection,
  updateConnection,
} from './api'
import type { ConnectionConfig } from './types'

const config: ConnectionConfig = {
  name: 'test-connection',
  rdbmsType: 'MYSQL',
  host: 'localhost',
  port: 3306,
  databaseName: 'mastermeister',
  username: 'user',
  password: 'password123',
  additionalParams: '',
}

describe('connectionApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('createConnection posts the config to /api/rdbms-connections', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('1', { status: 200 }))

    const result = await createConnection(config)

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/rdbms-connections',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(config) }),
    )
    expect(result).toBe(1)
  })

  it('updateConnection puts the config to /api/rdbms-connections/{id}', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    await updateConnection(42, config)

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/rdbms-connections/42',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(config) }),
    )
  })

  it('listConnections gets /api/rdbms-connections', async () => {
    const summaries = [{ id: 1, name: 'test-connection', rdbmsType: 'MYSQL', host: 'localhost', databaseName: 'db' }]
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(summaries), { status: 200 }))

    const result = await listConnections()

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections')
    expect(result).toEqual(summaries)
  })

  it('getConnection gets /api/rdbms-connections/{id}', async () => {
    const detail = {
      id: 42,
      name: 'test-connection',
      rdbmsType: 'MYSQL',
      host: 'localhost',
      port: 3306,
      databaseName: 'db',
      username: 'user',
      additionalParams: null,
    }
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(detail), { status: 200 }))

    const result = await getConnection(42)

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections/42')
    expect(result).toEqual(detail)
  })

  it('testConnection with a config posts to /api/rdbms-connections/test', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ success: true, message: 'ok' }), { status: 200 }))

    const result = await testConnection(config)

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/rdbms-connections/test',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(config) }),
    )
    expect(result).toEqual({ success: true, message: 'ok' })
  })

  it('testConnection with a connectionId posts to /api/rdbms-connections/{id}/test', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ success: false, message: 'timeout' }), { status: 200 }))

    const result = await testConnection(42)

    expect(fetchSpy).toHaveBeenCalledWith('/api/rdbms-connections/42/test', expect.objectContaining({ method: 'POST' }))
    expect(result).toEqual({ success: false, message: 'timeout' })
  })

  it('listAccessibleConnections gets /api/rdbms-connections/accessible', async () => {
    const summaries = [{ id: 1, name: 'test-connection', rdbmsType: 'MYSQL', host: 'localhost', databaseName: 'db' }]
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(summaries), { status: 200 }))

    const result = await listAccessibleConnections()

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections/accessible')
    expect(result).toEqual(summaries)
  })
})