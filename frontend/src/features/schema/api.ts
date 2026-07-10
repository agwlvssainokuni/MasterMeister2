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
import type { SchemaImportResult, TableDetail, TableMetadata } from './types'

export function importSchema(connectionId: number): Promise<SchemaImportResult> {
  return apiFetch<SchemaImportResult>(`/api/rdbms-connections/${connectionId}/schema-import`, { method: 'POST' })
}

export function listSchemas(connectionId: number): Promise<string[]> {
  return apiFetch<string[]>(`/api/rdbms-connections/${connectionId}/schemas`)
}

export function listTables(connectionId: number, schema: string): Promise<TableMetadata[]> {
  return apiFetch<TableMetadata[]>(
    `/api/rdbms-connections/${connectionId}/schemas/${encodeURIComponent(schema)}/tables`,
  )
}

export function getTableDetail(connectionId: number, schema: string, table: string): Promise<TableDetail> {
  return apiFetch<TableDetail>(
    `/api/rdbms-connections/${connectionId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}`,
  )
}