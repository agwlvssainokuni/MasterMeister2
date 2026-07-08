import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null })
  })

  it('starts with no current user and no token', () => {
    const state = useAuthStore.getState()
    expect(state.currentUser).toBeNull()
    expect(state.token).toBeNull()
  })

  it('login sets currentUser and token', () => {
    const user = { id: 1, email: 'user@example.com', role: 'USER' as const }

    useAuthStore.getState().login(user, 'a-token')

    const state = useAuthStore.getState()
    expect(state.currentUser).toEqual(user)
    expect(state.token).toBe('a-token')
  })

  it('logout clears currentUser and token', () => {
    useAuthStore.getState().login({ id: 1, email: 'user@example.com', role: 'USER' }, 'a-token')

    useAuthStore.getState().logout()

    const state = useAuthStore.getState()
    expect(state.currentUser).toBeNull()
    expect(state.token).toBeNull()
  })
})