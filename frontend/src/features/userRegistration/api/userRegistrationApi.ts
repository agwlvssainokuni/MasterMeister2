import { apiFetch } from '../../../api/apiClient'
import type { PendingUserSummary } from '../types'

export function requestRegistration(email: string): Promise<void> {
  return apiFetch<void>('/api/registrations', { method: 'POST', body: { email } })
}

export function completeRegistration(token: string, password: string): Promise<void> {
  return apiFetch<void>('/api/registrations/complete', { method: 'POST', body: { token, password } })
}

export function listPendingUsers(): Promise<PendingUserSummary[]> {
  return apiFetch<PendingUserSummary[]>('/api/registrations/pending')
}

export function approveUser(userId: number): Promise<void> {
  return apiFetch<void>(`/api/registrations/${userId}/approve`, { method: 'POST' })
}

export function rejectUser(userId: number): Promise<void> {
  return apiFetch<void>(`/api/registrations/${userId}/reject`, { method: 'POST' })
}