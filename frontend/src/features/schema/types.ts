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

export type TableType = 'TABLE' | 'VIEW'

export interface SchemaImportResult {
  success: boolean
  tableCount: number
  message: string
}

export interface TableMetadata {
  schemaName: string
  tableName: string
  tableType: TableType
  comment: string | null
}

export interface ColumnDetail {
  columnName: string
  dataType: string
  nullable: boolean
  comment: string | null
  ordinalPosition: number
  primaryKeySequence: number | null
}

export interface TableDetail {
  schemaName: string
  tableName: string
  tableType: TableType
  comment: string | null
  columns: ColumnDetail[]
}