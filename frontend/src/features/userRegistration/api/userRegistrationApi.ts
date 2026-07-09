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