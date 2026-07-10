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
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import type { UserSummary } from './types'

interface GroupMemberTableProps {
  members: UserSummary[]
  onRemove: (userId: number) => void
}

export function GroupMemberTable({ members, onRemove }: GroupMemberTableProps) {
  const [removeTarget, setRemoveTarget] = useState<UserSummary | null>(null)

  const columns: DataTableColumn<UserSummary>[] = [
    { key: 'email', header: 'メールアドレス' },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <button
          type="button"
          data-testid="group-member-table-remove-button"
          onClick={() => setRemoveTarget(row)}
        >
          削除
        </button>
      ),
    },
  ]

  return (
    <>
      <DataTable columns={columns} rows={members} getRowKey={(row) => row.id} />
      {removeTarget && (
        <ConfirmDialog
          message={`${removeTarget.email} をグループから削除しますか？`}
          onConfirm={() => {
            onRemove(removeTarget.id)
            setRemoveTarget(null)
          }}
          onCancel={() => setRemoveTarget(null)}
        />
      )}
    </>
  )
}