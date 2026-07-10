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

import { apiFetch } from '../../api/apiClient'
import type { GroupSummary, UserSummary } from './types'

export function createGroup(name: string): Promise<number> {
  return apiFetch<number>('/api/groups', { method: 'POST', body: { name } })
}

export function renameGroup(groupId: number, name: string): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}`, { method: 'PUT', body: { name } })
}

export function deleteGroup(groupId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}`, { method: 'DELETE' })
}

export function listGroups(): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>('/api/groups')
}

export function listGroupMembers(groupId: number): Promise<UserSummary[]> {
  return apiFetch<UserSummary[]>(`/api/groups/${groupId}/members`)
}

export function addUserToGroup(groupId: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/members`, { method: 'POST', body: { userId } })
}

export function removeUserFromGroup(groupId: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' })
}