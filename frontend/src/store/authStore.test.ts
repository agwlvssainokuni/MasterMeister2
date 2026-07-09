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
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
    sessionStorage.clear()
  })

  it('starts with no current user and no tokens', () => {
    const state = useAuthStore.getState()
    expect(state.currentUser).toBeNull()
    expect(state.token).toBeNull()
    expect(state.refreshToken).toBeNull()
  })

  it('setTokens sets currentUser, access token, and refresh token', () => {
    const user = { id: 1, email: 'user@example.com', role: 'USER' as const }

    useAuthStore.getState().setTokens(user, 'access-token', 'refresh-token')

    const state = useAuthStore.getState()
    expect(state.currentUser).toEqual(user)
    expect(state.token).toBe('access-token')
    expect(state.refreshToken).toBe('refresh-token')
  })

  it('clearTokens clears currentUser and both tokens', () => {
    useAuthStore
      .getState()
      .setTokens({ id: 1, email: 'user@example.com', role: 'USER' }, 'access-token', 'refresh-token')

    useAuthStore.getState().clearTokens()

    const state = useAuthStore.getState()
    expect(state.currentUser).toBeNull()
    expect(state.token).toBeNull()
    expect(state.refreshToken).toBeNull()
  })

  it('persists state to sessionStorage on setTokens', () => {
    const user = { id: 1, email: 'user@example.com', role: 'USER' as const }

    useAuthStore.getState().setTokens(user, 'access-token', 'refresh-token')

    const stored = JSON.parse(sessionStorage.getItem('auth-storage') ?? '{}')
    expect(stored.state.currentUser).toEqual(user)
    expect(stored.state.token).toBe('access-token')
    expect(stored.state.refreshToken).toBe('refresh-token')
  })

  it('clears the persisted sessionStorage state on clearTokens', () => {
    useAuthStore
      .getState()
      .setTokens({ id: 1, email: 'user@example.com', role: 'USER' }, 'access-token', 'refresh-token')

    useAuthStore.getState().clearTokens()

    const stored = JSON.parse(sessionStorage.getItem('auth-storage') ?? '{}')
    expect(stored.state.currentUser).toBeNull()
    expect(stored.state.token).toBeNull()
    expect(stored.state.refreshToken).toBeNull()
  })
})