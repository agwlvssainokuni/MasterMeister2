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

import { useEffect, useState } from 'react'
import { lookupPermission } from './api'
import { ConnectionSelector } from './ConnectionSelector'
import { PermissionForm } from './PermissionForm'
import { PermissionTree } from './PermissionTree'
import { PermissionYamlPanel } from './PermissionYamlPanel'
import { PrincipalSelector } from './PrincipalSelector'
import type { AuxPermissionType, Permission, PrincipalRef, SchemaTreeNode } from './types'

export function PermissionAssignmentPage() {
  const [connectionId, setConnectionId] = useState<number | null>(null)
  const [principal, setPrincipal] = useState<PrincipalRef | null>(null)
  const [selectedNode, setSelectedNode] = useState<SchemaTreeNode | null>(null)
  const [currentPermission, setCurrentPermission] = useState<Permission | null>(null)
  const [currentAuxPermissions, setCurrentAuxPermissions] = useState<Record<AuxPermissionType, boolean> | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const handleSelectConnection = (id: number) => {
    setConnectionId(id)
    setSelectedNode(null)
  }

  useEffect(() => {
    if (connectionId === null || principal === null || selectedNode === null) {
      setCurrentPermission(null)
      setCurrentAuxPermissions(null)
      return
    }
    setCurrentPermission(null)
    setCurrentAuxPermissions(null)
    lookupPermission(
      principal,
      connectionId,
      selectedNode.schema,
      selectedNode.table ?? null,
      selectedNode.column ?? null,
    ).then((result) => {
      setCurrentPermission(result.permission)
      setCurrentAuxPermissions({ CREATE: result.auxCreate, DELETE: result.auxDelete })
    })
  }, [connectionId, principal, selectedNode, reloadKey])

  return (
    <div className="permission-assignment-page" data-testid="permission-assignment-page">
      <h1>権限設定</h1>
      <div className="perm-toolbar">
        <ConnectionSelector selected={connectionId} onSelect={handleSelectConnection} />
        <PrincipalSelector selected={principal} onSelect={setPrincipal} />
      </div>
      {connectionId !== null && (
        <div className="perm-grid">
          <PermissionTree connectionId={connectionId} selectedNode={selectedNode} onSelectNode={setSelectedNode} />
          <div className="perm-grid-side">
            {principal && selectedNode && (
              <PermissionForm
                principal={principal}
                connectionId={connectionId}
                node={selectedNode}
                currentPermission={currentPermission}
                currentAuxPermissions={currentAuxPermissions}
                onSaved={() => setReloadKey((k) => k + 1)}
              />
            )}
            <PermissionYamlPanel connectionId={connectionId} />
          </div>
        </div>
      )}
    </div>
  )
}