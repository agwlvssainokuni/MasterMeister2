import { useAuthStore } from '../store/authStore'
import type { ErrorResponse } from '../types/api'

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

function buildRequestInit(options: ApiFetchOptions): RequestInit {
  const { body, headers, ...rest } = options
  const token = useAuthStore.getState().token

  return {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

// リフレッシュ自体は本関数（apiFetch）を経由しない素のfetchで行い、無限リトライを防止する
// （nfr-design-patterns.md 1.4、u2-auth-user-registration-code-generation-plan.md 11-2）。
async function refreshAccessToken(): Promise<boolean> {
  const { refreshToken, currentUser } = useAuthStore.getState()
  if (!refreshToken || !currentUser) {
    return false
  }

  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    return false
  }

  const refreshed = (await response.json()) as { accessToken: string; refreshToken: string }
  useAuthStore.getState().setTokens(currentUser, refreshed.accessToken, refreshed.refreshToken)
  return true
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    useAuthStore.getState().clearTokens()
    window.location.href = '/login'
    throw new ApiError(401, 'UNAUTHENTICATED', 'Authentication required')
  }

  if (!response.ok) {
    const errorBody: ErrorResponse = await response.json().catch(() => ({
      error: 'UNKNOWN_ERROR',
      message: response.statusText,
    }))
    throw new ApiError(response.status, errorBody.error, errorBody.message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const response = await fetch(path, buildRequestInit(options))

  if (response.status === 401) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      const retryResponse = await fetch(path, buildRequestInit(options))
      return handleResponse<T>(retryResponse)
    }
  }

  return handleResponse<T>(response)
}