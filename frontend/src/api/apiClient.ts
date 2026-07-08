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

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options
  const token = useAuthStore.getState().token

  const response = await fetch(path, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401) {
    useAuthStore.getState().logout()
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