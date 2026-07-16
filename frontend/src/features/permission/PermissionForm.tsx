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

import { type FormEvent, useEffect, useState } from 'react'
import { setAuxPermission as setAuxPermissionApi, setPermission as setPermissionApi } from './api'
import type { AuxPermissionType, Permission, PrincipalRef, SchemaTreeNode } from './types'

interface PermissionFormProps {
  principal: PrincipalRef
  connectionId: number
  node: SchemaTreeNode
  currentPermission: Permission | null
  currentAuxPermissions: Record<AuxPermissionType, boolean> | null
  onSaved?: () => void
}

const PERMISSIONS: Permission[] = ['NONE', 'READ', 'UPDATE']
const AUX_TYPES: AuxPermissionType[] = ['CREATE', 'DELETE']
const emptyAuxPermissions: Record<AuxPermissionType, boolean> = { CREATE: false, DELETE: false }

export function PermissionForm({
  principal,
  connectionId,
  node,
  currentPermission,
  currentAuxPermissions,
  onSaved,
}: PermissionFormProps) {
  const [permission, setPermission] = useState<Permission>(currentPermission ?? 'NONE')
  const [auxPermissions, setAuxPermissions] = useState<Record<AuxPermissionType, boolean>>(
    currentAuxPermissions ?? emptyAuxPermissions,
  )
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // ノード（スキーマ/テーブル/カラム）選択の切り替えごとにフォーム状態を初期化する。
  useEffect(() => {
    setPermission(currentPermission ?? 'NONE')
    setAuxPermissions(currentAuxPermissions ?? emptyAuxPermissions)
    setError(null)
  }, [node, currentPermission, currentAuxPermissions])

  const showAuxPermissions = node.level !== 'column'

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await setPermissionApi(principal, connectionId, node.schema, node.table ?? null, node.column ?? null, permission)
      if (showAuxPermissions) {
        for (const auxType of AUX_TYPES) {
          await setAuxPermissionApi(
            principal,
            connectionId,
            node.schema,
            node.table ?? null,
            auxType,
            auxPermissions[auxType],
          )
        }
      }
      onSaved?.()
    } catch (e) {
      setError(e instanceof Error ? e.message : '権限の保存に失敗しました')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="permission-form" data-testid="permission-form" onSubmit={handleSubmit}>
      {error && (
        <p role="alert" data-testid="permission-form-error">
          {error}
        </p>
      )}
      <label>
        権限
        <select
          data-testid="permission-form-permission-select"
          value={permission}
          onChange={(e) => setPermission(e.target.value as Permission)}
        >
          {PERMISSIONS.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
      {showAuxPermissions && (
        <fieldset>
          <legend>補助権限</legend>
          {AUX_TYPES.map((auxType) => (
            <label key={auxType}>
              <input
                type="checkbox"
                data-testid={`permission-form-aux-${auxType.toLowerCase()}-checkbox`}
                checked={auxPermissions[auxType]}
                onChange={(e) => setAuxPermissions((prev) => ({ ...prev, [auxType]: e.target.checked }))}
              />
              {auxType}
            </label>
          ))}
        </fieldset>
      )}
      <button type="submit" className="btn-primary" data-testid="permission-form-submit-button" disabled={submitting}>
        保存
      </button>
    </form>
  )
}