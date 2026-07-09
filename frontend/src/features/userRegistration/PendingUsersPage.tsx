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

import { useCallback, useEffect, useState } from 'react'
import { ToastNotification } from '../../components/ToastNotification'
import { approveUser, listPendingUsers, rejectUser } from './api/userRegistrationApi'
import { PendingUsersTable } from './PendingUsersTable'
import type { PendingUserSummary } from './types'

interface Toast {
  message: string
  severity: 'success' | 'error'
}

export function PendingUsersPage() {
  const [users, setUsers] = useState<PendingUserSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<Toast | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await listPendingUsers())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleApprove = async (userId: number) => {
    try {
      await approveUser(userId)
      setToast({ message: '承認しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: '承認に失敗しました', severity: 'error' })
    }
  }

  const handleReject = async (userId: number) => {
    try {
      await rejectUser(userId)
      setToast({ message: '却下しました', severity: 'success' })
      await refresh()
    } catch {
      setToast({ message: '却下に失敗しました', severity: 'error' })
    }
  }

  return (
    <div className="pending-users-page" data-testid="pending-users-page">
      <h1>承認待ちユーザ</h1>
      {toast && <ToastNotification message={toast.message} severity={toast.severity} />}
      {loading ? <p>読み込み中...</p> : <PendingUsersTable users={users} onApprove={handleApprove} onReject={handleReject} />}
    </div>
  )
}