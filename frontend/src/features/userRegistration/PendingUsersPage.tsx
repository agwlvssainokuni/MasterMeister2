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