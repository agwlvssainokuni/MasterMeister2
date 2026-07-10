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
import type { GroupSummary } from './types'

interface GroupTableProps {
  groups: GroupSummary[]
  onOpenDetail: (groupId: number) => void
  onRename: (groupId: number, newName: string) => void
  onDelete: (groupId: number) => void
}

export function GroupTable({ groups, onOpenDetail, onRename, onDelete }: GroupTableProps) {
  const [renamingGroupId, setRenamingGroupId] = useState<number | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<GroupSummary | null>(null)

  const startRename = (group: GroupSummary) => {
    setRenamingGroupId(group.id)
    setRenameValue(group.name)
  }

  const commitRename = (groupId: number) => {
    onRename(groupId, renameValue)
    setRenamingGroupId(null)
  }

  const columns: DataTableColumn<GroupSummary>[] = [
    {
      key: 'name',
      header: 'グループ名',
      render: (row) =>
        renamingGroupId === row.id ? (
          <input
            type="text"
            data-testid="group-table-rename-input"
            value={renameValue}
            onChange={(e) => setRenameValue(e.target.value)}
          />
        ) : (
          row.name
        ),
    },
    {
      key: 'createdAt',
      header: '作成日時',
      render: (row) => new Date(row.createdAt).toLocaleString(),
    },
    {
      key: 'actions',
      header: '操作',
      render: (row) => (
        <>
          <button
            type="button"
            data-testid="group-table-detail-button"
            onClick={() => onOpenDetail(row.id)}
          >
            詳細
          </button>
          {renamingGroupId === row.id ? (
            <>
              <button
                type="button"
                data-testid="group-table-rename-commit-button"
                onClick={() => commitRename(row.id)}
              >
                保存
              </button>
              <button
                type="button"
                data-testid="group-table-rename-cancel-button"
                onClick={() => setRenamingGroupId(null)}
              >
                キャンセル
              </button>
            </>
          ) : (
            <button
              type="button"
              data-testid="group-table-rename-button"
              onClick={() => startRename(row)}
            >
              名称変更
            </button>
          )}
          <button
            type="button"
            data-testid="group-table-delete-button"
            onClick={() => setDeleteTarget(row)}
          >
            削除
          </button>
        </>
      ),
    },
  ]

  return (
    <>
      <DataTable columns={columns} rows={groups} getRowKey={(row) => row.id} />
      {deleteTarget && (
        <ConfirmDialog
          message={`グループ「${deleteTarget.name}」を削除しますか？所属ユーザのグループ経由の権限設定も併せて削除されます。`}
          onConfirm={() => {
            onDelete(deleteTarget.id)
            setDeleteTarget(null)
          }}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </>
  )
}