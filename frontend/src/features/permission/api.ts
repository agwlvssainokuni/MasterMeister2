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
import { useAuthStore } from '../../store/authStore'
import type { AuxPermissionType, ImportResult, Permission, PrincipalRef } from './types'

export function setPermission(
  principal: PrincipalRef,
  connectionId: number,
  schema: string,
  table: string | null,
  column: string | null,
  permission: Permission,
): Promise<void> {
  return apiFetch<void>(`/api/rdbms-connections/${connectionId}/permissions`, {
    method: 'PUT',
    body: { principal, schema, table, column, permission, auxType: null, granted: null },
  })
}

export function setAuxPermission(
  principal: PrincipalRef,
  connectionId: number,
  schema: string,
  table: string | null,
  auxType: AuxPermissionType,
  granted: boolean,
): Promise<void> {
  return apiFetch<void>(`/api/rdbms-connections/${connectionId}/permissions`, {
    method: 'PUT',
    body: { principal, schema, table, column: null, permission: null, auxType, granted },
  })
}

// エクスポート（blobダウンロード）・インポート（FormDataアップロード）はJSON専用のapiFetchを
// バイパスし、useAuthStoreから直接トークンを取得する素のfetchで実装する
// （frontend-components.md item 11-2、Step 2完了後に確定したAI決定）。
export async function exportPermissionsAsYaml(connectionId: number): Promise<Blob> {
  const token = useAuthStore.getState().token
  const response = await fetch(`/api/rdbms-connections/${connectionId}/permissions/export`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error('エクスポートに失敗しました')
  }
  return response.blob()
}

export async function importPermissionsFromYaml(connectionId: number, file: File): Promise<ImportResult> {
  const token = useAuthStore.getState().token
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`/api/rdbms-connections/${connectionId}/permissions/import`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  })
  const body: { success?: boolean; message?: string } | null = await response.json().catch(() => null)
  if (response.ok && body && typeof body.success === 'boolean') {
    return { success: body.success, message: body.message ?? '' }
  }
  return { success: false, message: body?.message ?? 'インポートに失敗しました' }
}