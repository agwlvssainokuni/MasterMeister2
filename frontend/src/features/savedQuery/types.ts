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

export type Visibility = 'PUBLIC' | 'PRIVATE'

export interface SavedQuerySummary {
  id: number
  name: string
  visibility: Visibility
  retired: boolean
  ownerId: number
}

export interface SavedQueryDetail {
  id: number
  ownerId: number
  connectionId: number
  name: string
  sql: string
  visibility: Visibility
  retired: boolean
  executionCount: number
  createdAt: string
  updatedAt: string
}
