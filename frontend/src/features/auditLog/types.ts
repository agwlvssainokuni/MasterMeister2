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

export type EventCategory = 'AUTHENTICATION' | 'ADMIN_OPERATION' | 'DATA_ACCESS'

export type EventType =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'LOGOUT'
  | 'USER_REGISTRATION_APPROVED'
  | 'USER_REGISTRATION_REJECTED'
  | 'RDBMS_CONNECTION_CHANGED'
  | 'SCHEMA_IMPORTED'
  | 'GROUP_CHANGED'
  | 'PERMISSION_CHANGED'
  | 'PERMISSION_YAML_EXPORTED'
  | 'PERMISSION_YAML_IMPORTED'
  | 'LARGE_RECORD_READ'
  | 'MASTER_DATA_MUTATION'
  | 'QUERY_EXECUTED'

export type AuditResult = 'SUCCESS' | 'FAILURE'

export const EVENT_CATEGORY_OPTIONS: EventCategory[] = ['AUTHENTICATION', 'ADMIN_OPERATION', 'DATA_ACCESS']

export const EVENT_TYPES_BY_CATEGORY: Record<EventCategory, EventType[]> = {
  AUTHENTICATION: ['LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT'],
  ADMIN_OPERATION: [
    'USER_REGISTRATION_APPROVED',
    'USER_REGISTRATION_REJECTED',
    'RDBMS_CONNECTION_CHANGED',
    'SCHEMA_IMPORTED',
    'GROUP_CHANGED',
    'PERMISSION_CHANGED',
    'PERMISSION_YAML_EXPORTED',
    'PERMISSION_YAML_IMPORTED',
  ],
  DATA_ACCESS: ['LARGE_RECORD_READ', 'MASTER_DATA_MUTATION', 'QUERY_EXECUTED'],
}

export interface AuditLog {
  id: number
  occurredAt: string
  userId: number | null
  connectionId: number | null
  eventCategory: EventCategory
  eventType: EventType
  result: AuditResult
  targetDescription: string | null
  summaryMessage: string | null
}

export interface AuditLogFilter {
  dateFrom?: string
  dateTo?: string
  userId?: number
  eventCategory?: EventCategory
  eventType?: EventType
}