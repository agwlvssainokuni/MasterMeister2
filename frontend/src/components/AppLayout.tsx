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

import type { ReactNode } from 'react'
import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { listAccessibleConnections } from '../features/rdbmsConnection/api'
import { useAuth } from '../hooks/useAuth'
import { useConnection } from '../hooks/useConnection'

interface AppLayoutProps {
  children: ReactNode
}

const MASTER_DATA_DETAIL_PATTERN = /^\/master-data\/[^/]+\/[^/]+\/[^/]+$/

export function AppLayout({ children }: AppLayoutProps) {
  const { currentUser, isAuthenticated, logout } = useAuth()
  const { connectionId, connections, setConnectionId, setConnections, clearConnection } = useConnection()
  const navigate = useNavigate()
  const location = useLocation()
  const hasMountedWithConnection = useRef(false)

  useEffect(() => {
    if (isAuthenticated && connections.length === 0) {
      listAccessibleConnections().then(setConnections)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  useEffect(() => {
    if (!hasMountedWithConnection.current) {
      hasMountedWithConnection.current = true
      return
    }
    if (MASTER_DATA_DETAIL_PATTERN.test(location.pathname)) {
      navigate('/master-data')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionId])

  const handleLogout = () => {
    logout()
    clearConnection()
  }

  return (
    <div className="app-layout">
      <header className="app-layout-header">
        <nav data-testid="app-layout-nav">
          <a href="/" data-testid="app-layout-nav-home">
            MasterMeister
          </a>
          {isAuthenticated && (
            <a href="/master-data" data-testid="app-layout-nav-master-data">
              マスタデータ
            </a>
          )}
          {isAuthenticated && (
            <a href="/query-builder" data-testid="app-layout-nav-query-builder">
              クエリビルダー
            </a>
          )}
          {isAuthenticated && (
            <a href="/saved-queries" data-testid="app-layout-nav-saved-queries">
              保存クエリ
            </a>
          )}
          {isAuthenticated && (
            <a href="/query-execution" data-testid="app-layout-nav-query-execution">
              クエリ実行
            </a>
          )}
          {isAuthenticated && (
            <a href="/query-history" data-testid="app-layout-nav-query-history">
              クエリ履歴
            </a>
          )}
          {isAuthenticated && currentUser?.role === 'ADMIN' && (
            <a href="/admin/pending-users" data-testid="app-layout-nav-pending-users">
              承認待ちユーザー
            </a>
          )}
          {isAuthenticated && currentUser?.role === 'ADMIN' && (
            <a href="/admin/audit-logs" data-testid="app-layout-nav-audit-logs">
              監査ログ
            </a>
          )}
          {isAuthenticated && currentUser?.role === 'ADMIN' && (
            <a href="/admin/rdbms-connections" data-testid="app-layout-nav-rdbms-connections">
              RDBMS接続管理
            </a>
          )}
          {isAuthenticated && currentUser?.role === 'ADMIN' && (
            <a href="/admin/groups" data-testid="app-layout-nav-groups">
              グループ管理
            </a>
          )}
          {isAuthenticated && currentUser?.role === 'ADMIN' && (
            <a href="/admin/permissions" data-testid="app-layout-nav-permissions">
              権限設定
            </a>
          )}
          {isAuthenticated && (
            <label>
              対象接続
              <select
                data-testid="app-layout-connection-select"
                value={connectionId ?? ''}
                onChange={(e) => setConnectionId(Number(e.target.value))}
              >
                <option value="" disabled>
                  選択してください
                </option>
                {connections.map((connection) => (
                  <option key={connection.id} value={connection.id}>
                    {connection.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          {isAuthenticated && (
            <button type="button" data-testid="app-layout-nav-logout" onClick={handleLogout}>
              ログアウト
            </button>
          )}
        </nav>
      </header>
      <main className="app-layout-main">{children}</main>
    </div>
  )
}