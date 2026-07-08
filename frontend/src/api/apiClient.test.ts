import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { apiFetch, ApiError } from './apiClient'

describe('apiFetch', () => {
  const originalLocation = window.location

  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null })
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { href: '' },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    })
  })

  it('returns parsed JSON on a successful response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ value: 42 }), { status: 200 }),
    )

    const result = await apiFetch<{ value: number }>('/api/example')

    expect(result).toEqual({ value: 42 })
  })

  it('attaches the Authorization header when a token is present', async () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'admin@example.com', role: 'ADMIN' },
      token: 'test-token',
    })
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }))

    await apiFetch('/api/example')

    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer test-token')
  })

  it('logs out and redirects to /login on a 401 response', async () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'admin@example.com', role: 'ADMIN' },
      token: 'test-token',
    })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))

    await expect(apiFetch('/api/example')).rejects.toBeInstanceOf(ApiError)

    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(useAuthStore.getState().token).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('parses the common error DTO and throws ApiError on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ error: 'VALIDATION_ERROR', message: 'invalid input' }), {
        status: 400,
      }),
    )

    const error = await apiFetch('/api/example').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).code).toBe('VALIDATION_ERROR')
    expect((error as ApiError).message).toBe('invalid input')
  })
})