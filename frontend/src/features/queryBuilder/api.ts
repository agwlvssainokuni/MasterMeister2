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
import type { ColumnRef, GeneratedSql, ParseResult, QueryBuilderModel, TableRef } from './types'

export function listSelectableSchemas(connectionId: number): Promise<string[]> {
  return apiFetch<string[]>(`/api/query-builder/${connectionId}/schemas`)
}

export function listSelectableTables(connectionId: number, schema: string): Promise<TableRef[]> {
  return apiFetch<TableRef[]>(
    `/api/query-builder/${connectionId}/schemas/${encodeURIComponent(schema)}/tables`,
  )
}

export function listSelectableColumns(
  connectionId: number,
  schema: string,
  table: string,
): Promise<ColumnRef[]> {
  return apiFetch<ColumnRef[]>(
    `/api/query-builder/${connectionId}/schemas/${encodeURIComponent(schema)}/tables/`
      + `${encodeURIComponent(table)}/columns`,
  )
}

export function generateSql(connectionId: number, model: QueryBuilderModel): Promise<GeneratedSql> {
  return apiFetch<GeneratedSql>(`/api/query-builder/${connectionId}/generate`, {
    method: 'POST',
    body: model,
  })
}

export function parseSql(connectionId: number, rawSql: string): Promise<ParseResult> {
  return apiFetch<ParseResult>(`/api/query-builder/${connectionId}/parse`, {
    method: 'POST',
    body: { rawSql },
  })
}