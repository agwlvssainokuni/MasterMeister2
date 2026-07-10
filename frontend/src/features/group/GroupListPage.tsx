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

import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ToastNotification } from '../../components/ToastNotification'
import type { ToastSeverity } from '../../components/ToastNotification'
import { createGroup, deleteGroup, listGroups, renameGroup } from './api'
import { GroupTable } from './GroupTable'
import type { GroupSummary } from './types'

interface Toast {
  message: string
  severity: ToastSeverity
}

export function GroupListPage() {
  const navigate = useNavigate()
  const [groups, setGroups] = useState<GroupSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [newGroupName, setNewGroupName] = useState('')
  const [toast, setToast] = useState<Toast | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setGroups(await listGroups())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await createGroup(newGroupName)
      setNewGroupName('')
      setToast({ message: 'グループを作成しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: 'グループの作成に失敗しました', severity: 'error' })
    }
  }

  const handleRename = async (groupId: number, newName: string) => {
    try {
      await renameGroup(groupId, newName)
      setToast({ message: 'グループ名を変更しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: 'グループ名の変更に失敗しました', severity: 'error' })
    }
  }

  const handleDelete = async (groupId: number) => {
    try {
      await deleteGroup(groupId)
      setToast({ message: 'グループを削除しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: 'グループの削除に失敗しました', severity: 'error' })
    }
  }

  return (
    <div className="group-list-page" data-testid="group-list-page">
      <h1>グループ管理</h1>
      {toast && <ToastNotification message={toast.message} severity={toast.severity} />}
      <form onSubmit={handleCreate}>
        <input
          type="text"
          data-testid="group-list-page-new-name-input"
          value={newGroupName}
          onChange={(e) => setNewGroupName(e.target.value)}
          required
        />
        <button type="submit" data-testid="group-list-page-new-button">
          新規作成
        </button>
      </form>
      {loading ? (
        <p>読み込み中...</p>
      ) : (
        <GroupTable
          groups={groups}
          onOpenDetail={(groupId) => navigate(`/admin/groups/${groupId}`)}
          onRename={handleRename}
          onDelete={handleDelete}
        />
      )}
    </div>
  )
}