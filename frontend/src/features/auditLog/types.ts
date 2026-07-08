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