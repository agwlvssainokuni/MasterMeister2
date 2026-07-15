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

import type { PageResult } from '../../types/api'

export type TableType = 'TABLE' | 'VIEW'

export type Permission = 'NONE' | 'READ' | 'UPDATE'

export interface TableSummary {
  schemaName: string
  tableName: string
  tableType: TableType
  comment: string | null
  effectivePermission: Permission
  canCreate: boolean
  canDelete: boolean
}

export interface ColumnMetadata {
  columnName: string
  dataType: string
  nullable: boolean
  primaryKeySequence: number | null
  effectivePermission: Permission
}

export interface RecordListResult {
  columns: ColumnMetadata[]
  records: PageResult<unknown[]>
}

export type FilterMode = 'UI' | 'RAW'

export type Operator = 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'LIKE' | 'IS_NULL' | 'IS_NOT_NULL'

export interface UiCondition {
  columnName: string
  operator: Operator
  value: unknown
}

export type SortDirection = 'ASC' | 'DESC'

export interface UiSort {
  columnName: string
  direction: SortDirection
}

export interface FilterCriteria {
  mode: FilterMode
  uiConditions: UiCondition[]
  uiSorts: UiSort[]
  rawWhere: string | null
  rawOrderBy: string | null
}

export interface RecordCreate {
  values: Record<string, unknown>
}

export interface RecordUpdate {
  primaryKeyValues: Record<string, unknown>
  changedValues: Record<string, unknown>
}

export interface RecordDelete {
  primaryKeyValues: Record<string, unknown>
}

export interface MutationRequest {
  creates: RecordCreate[]
  updates: RecordUpdate[]
  deletes: RecordDelete[]
}

export interface MutationResult {
  success: boolean
  createdCount: number
  updatedCount: number
  deletedCount: number
  errorMessage: string | null
}