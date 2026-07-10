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

export type PrincipalType = 'USER' | 'GROUP'

export interface PrincipalRef {
  principalType: PrincipalType
  principalId: number
}

export type Permission = 'NONE' | 'READ' | 'UPDATE'

export type AuxPermissionType = 'CREATE' | 'DELETE'

export type SchemaTreeNodeLevel = 'schema' | 'table' | 'column'

export interface SchemaTreeNode {
  level: SchemaTreeNodeLevel
  schema: string
  table?: string
  column?: string
}

export interface ImportResult {
  success: boolean
  message: string
}