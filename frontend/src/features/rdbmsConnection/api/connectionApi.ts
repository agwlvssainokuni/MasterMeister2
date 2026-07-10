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
import type { ConnectionConfig, ConnectionDetail, ConnectionSummary, ConnectionTestResult } from '../types'

export function createConnection(config: ConnectionConfig): Promise<number> {
  return apiFetch<number>('/api/rdbms-connections', { method: 'POST', body: config })
}

export function updateConnection(connectionId: number, config: ConnectionConfig): Promise<void> {
  return apiFetch<void>(`/api/rdbms-connections/${connectionId}`, { method: 'PUT', body: config })
}

export function listConnections(): Promise<ConnectionSummary[]> {
  return apiFetch<ConnectionSummary[]>('/api/rdbms-connections')
}

export function getConnection(connectionId: number): Promise<ConnectionDetail> {
  return apiFetch<ConnectionDetail>(`/api/rdbms-connections/${connectionId}`)
}

// フロー2の2パターン（保存前の設定値によるテスト/保存済み接続IDによるテスト）を単一のAPI関数名に
// オーバーロードで束ねる（frontend-components.md connectionApi.ts表）。
export function testConnection(config: ConnectionConfig): Promise<ConnectionTestResult>
export function testConnection(connectionId: number): Promise<ConnectionTestResult>
export function testConnection(target: ConnectionConfig | number): Promise<ConnectionTestResult> {
  if (typeof target === 'number') {
    return apiFetch<ConnectionTestResult>(`/api/rdbms-connections/${target}/test`, { method: 'POST' })
  }
  return apiFetch<ConnectionTestResult>('/api/rdbms-connections/test', { method: 'POST', body: target })
}