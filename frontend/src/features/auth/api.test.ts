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
import { decodeAccessToken, login, logout, refresh } from './api'

describe('authApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('login posts email and password to /api/auth/login', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ accessToken: 'a', refreshToken: 'r' }), { status: 200 }))

    const result = await login('user@example.com', 'password123')

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'user@example.com', password: 'password123' }),
      }),
    )
    expect(result).toEqual({ accessToken: 'a', refreshToken: 'r' })
  })

  it('refresh posts refreshToken to /api/auth/refresh', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ accessToken: 'a2', refreshToken: 'r2' }), { status: 200 }))

    await refresh('old-refresh-token')

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/auth/refresh',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ refreshToken: 'old-refresh-token' }) }),
    )
  })

  it('logout posts refreshToken to /api/auth/logout', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    await logout('a-refresh-token')

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ refreshToken: 'a-refresh-token' }) }),
    )
  })

  it('decodeAccessToken extracts userId and role from the JWT payload', () => {
    const payload = btoa(JSON.stringify({ sub: '42', role: 'ADMIN' }))
    const token = `header.${payload}.signature`

    const decoded = decodeAccessToken(token)

    expect(decoded).toEqual({ userId: 42, role: 'ADMIN' })
  })
})