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
import { Link } from 'react-router-dom'
import { ToastNotification } from '../../components/ToastNotification'
import type { ToastSeverity } from '../../components/ToastNotification'
import { listConnections, testConnection } from './api/connectionApi'
import { ConnectionTable } from './ConnectionTable'
import type { ConnectionSummary } from './types'

interface Toast {
  message: string
  severity: ToastSeverity
}

export function ConnectionListPage() {
  const [connections, setConnections] = useState<ConnectionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<Toast | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setConnections(await listConnections())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const handleTest = async (connectionId: number) => {
    try {
      const result = await testConnection(connectionId)
      setToast({
        message: result.success ? '接続に成功しました' : `接続に失敗しました: ${result.message}`,
        severity: result.success ? 'success' : 'error',
      })
    } catch {
      setToast({ message: '接続テストに失敗しました', severity: 'error' })
    }
  }

  // SchemaImportPanel（features/schema）の起動はStep 11 item 11-2で結線する
  // （frontend-components.mdの通り、独立ルートを持たずここから起動するモーダル/パネルとする）。
  const handleImportSchema = (connectionId: number) => {
    void connectionId
  }

  return (
    <div className="connection-list-page" data-testid="connection-list-page">
      <h1>RDBMS接続管理</h1>
      <Link to="/admin/rdbms-connections/new" data-testid="connection-list-page-new-button">
        新規登録
      </Link>
      {toast && <ToastNotification message={toast.message} severity={toast.severity} />}
      {loading ? (
        <p>読み込み中...</p>
      ) : (
        <ConnectionTable connections={connections} onTest={handleTest} onImportSchema={handleImportSchema} />
      )}
    </div>
  )
}