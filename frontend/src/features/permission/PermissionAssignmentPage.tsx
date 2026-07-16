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

import { useState } from 'react'
import { ConnectionSelector } from './ConnectionSelector'
import { PermissionForm } from './PermissionForm'
import { PermissionTree } from './PermissionTree'
import { PermissionYamlPanel } from './PermissionYamlPanel'
import { PrincipalSelector } from './PrincipalSelector'
import type { PrincipalRef, SchemaTreeNode } from './types'

export function PermissionAssignmentPage() {
  const [connectionId, setConnectionId] = useState<number | null>(null)
  const [principal, setPrincipal] = useState<PrincipalRef | null>(null)
  const [selectedNode, setSelectedNode] = useState<SchemaTreeNode | null>(null)

  const handleSelectConnection = (id: number) => {
    setConnectionId(id)
    setSelectedNode(null)
  }

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
                currentPermission={null}
                currentAuxPermissions={null}
              />
            )}
            <PermissionYamlPanel connectionId={connectionId} />
          </div>
        </div>
      )}
    </div>
  )
}