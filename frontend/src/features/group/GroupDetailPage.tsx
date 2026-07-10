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
import { useParams } from 'react-router-dom'
import { ToastNotification } from '../../components/ToastNotification'
import type { ToastSeverity } from '../../components/ToastNotification'
import { addUserToGroup, listGroupMembers, listGroups, removeUserFromGroup } from './api'
import { GroupMemberTable } from './GroupMemberTable'
import type { GroupSummary, UserSummary } from './types'

interface Toast {
  message: string
  severity: ToastSeverity
}

export function GroupDetailPage() {
  const { id } = useParams<{ id: string }>()
  const groupId = Number(id)

  const [group, setGroup] = useState<GroupSummary | null>(null)
  const [members, setMembers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [newUserId, setNewUserId] = useState('')
  const [toast, setToast] = useState<Toast | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const [groups, groupMembers] = await Promise.all([listGroups(), listGroupMembers(groupId)])
      setGroup(groups.find((g) => g.id === groupId) ?? null)
      setMembers(groupMembers)
    } finally {
      setLoading(false)
    }
  }, [groupId])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleAddUser = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await addUserToGroup(groupId, Number(newUserId))
      setNewUserId('')
      setToast({ message: 'ユーザを追加しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: 'ユーザの追加に失敗しました', severity: 'error' })
    }
  }

  const handleRemove = async (userId: number) => {
    try {
      await removeUserFromGroup(groupId, userId)
      setToast({ message: 'ユーザを削除しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: 'ユーザの削除に失敗しました', severity: 'error' })
    }
  }

  return (
    <div className="group-detail-page" data-testid="group-detail-page">
      <h1>{group ? `グループ「${group.name}」` : 'グループ詳細'}</h1>
      {toast && <ToastNotification message={toast.message} severity={toast.severity} />}
      <form onSubmit={handleAddUser}>
        <label>
          ユーザID
          <input
            type="number"
            data-testid="group-detail-page-new-user-id-input"
            value={newUserId}
            onChange={(e) => setNewUserId(e.target.value)}
            required
          />
        </label>
        <button type="submit" data-testid="group-detail-page-add-user-button">
          所属ユーザ追加
        </button>
      </form>
      {loading ? <p>読み込み中...</p> : <GroupMemberTable members={members} onRemove={handleRemove} />}
    </div>
  )
}