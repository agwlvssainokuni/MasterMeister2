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
import { getTableDetail, importSchema, listSchemas, listTables } from './schemaApi'

describe('schemaApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('importSchema posts to /api/rdbms-connections/{connectionId}/schema-import', async () => {
    const result = { success: true, tableCount: 3, message: 'ok' }
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(result), { status: 200 }))

    const actual = await importSchema(42)

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/rdbms-connections/42/schema-import',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(actual).toEqual(result)
  })

  it('listSchemas gets /api/rdbms-connections/{connectionId}/schemas', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(['public', 'app']), { status: 200 }))

    const actual = await listSchemas(42)

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections/42/schemas')
    expect(actual).toEqual(['public', 'app'])
  })

  it('listTables gets /api/rdbms-connections/{connectionId}/schemas/{schema}/tables with schema name escaped', async () => {
    const tables = [{ schemaName: 'my schema', tableName: 'users', tableType: 'TABLE', comment: null }]
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(tables), { status: 200 }))

    const actual = await listTables(42, 'my schema')

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections/42/schemas/my%20schema/tables')
    expect(actual).toEqual(tables)
  })

  it('getTableDetail gets /api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table} with names escaped', async () => {
    const detail = { schemaName: 'public', tableName: 'my table', tableType: 'TABLE', comment: null, columns: [] }
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(detail), { status: 200 }))

    const actual = await getTableDetail(42, 'public', 'my table')

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/rdbms-connections/42/schemas/public/tables/my%20table')
    expect(actual).toEqual(detail)
  })
})