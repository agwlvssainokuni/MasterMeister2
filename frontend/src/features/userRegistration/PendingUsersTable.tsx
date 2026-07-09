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
import type { PendingUserSummary } from './types'

interface PendingUsersTableProps {
  users: PendingUserSummary[]
  onApprove: (userId: number) => void
  onReject: (userId: number) => void
}

export function PendingUsersTable({ users, onApprove, onReject }: PendingUsersTableProps) {
  const [rejectTarget, setRejectTarget] = useState<PendingUserSummary | null>(null)

  const columns: DataTableColumn<PendingUserSummary>[] = [
    { key: 'email', header: 'メールアドレス' },
    {
      key: 'createdAt',
      header: '登録完了日時',
      render: (row) => new Date(row.createdAt).toLocaleString(),
    },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <>
          <button
            type="button"
            data-testid="pending-users-table-approve-button"
            onClick={() => onApprove(row.id)}
          >
            承認
          </button>
          <button
            type="button"
            data-testid="pending-users-table-reject-button"
            onClick={() => setRejectTarget(row)}
          >
            却下
          </button>
        </>
      ),
    },
  ]

  return (
    <>
      <DataTable columns={columns} rows={users} getRowKey={(row) => row.id} />
      {rejectTarget && (
        <ConfirmDialog
          message={`${rejectTarget.email} の登録申請を却下しますか？`}
          onConfirm={() => {
            onReject(rejectTarget.id)
            setRejectTarget(null)
          }}
          onCancel={() => setRejectTarget(null)}
        />
      )}
    </>
  )
}