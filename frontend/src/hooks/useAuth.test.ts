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
import { useAuthStore } from '../store/authStore'
import { useAuth } from './useAuth'

describe('useAuth', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
  })

  it('reports unauthenticated when there is no current user or token', () => {
    const { result } = renderHook(() => useAuth())

    expect(result.current.isAuthenticated).toBe(false)
  })

  it('reports authenticated and exposes the current user after setTokens', () => {
    const { result } = renderHook(() => useAuth())
    const user = { id: 1, email: 'admin@example.com', role: 'ADMIN' as const }

    act(() => {
      result.current.setTokens(user, 'access-token', 'refresh-token')
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.currentUser).toEqual(user)
    expect(result.current.token).toBe('access-token')
  })

  it('reports unauthenticated again after logout', () => {
    const { result } = renderHook(() => useAuth())
    act(() => {
      result.current.setTokens({ id: 1, email: 'admin@example.com', role: 'ADMIN' }, 'access-token', 'refresh-token')
    })

    act(() => {
      result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.currentUser).toBeNull()
  })
})