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

function isActivePath(pathname: string, path: string): boolean {
  return pathname === path || pathname.startsWith(`${path}/`)
}

export function AppLayout({ children }: AppLayoutProps) {
  const { currentUser, isAuthenticated, logout } = useAuth()
  const { connectionId, connections, setConnectionId, setConnections, clearConnection } = useConnection()
  const navigate = useNavigate()
  const location = useLocation()
  const hasMountedWithConnection = useRef(false)
  const isActive = (path: string) => isActivePath(location.pathname, path)

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
        <a className="app-header-brand" href="/" data-testid="app-layout-nav-home">
          MasterMeister <small>MASTER DATA CONSOLE</small>
        </a>
        <div className="app-header-right">
          {isAuthenticated && (
            <label className="conn-select">
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
          {isAuthenticated && currentUser && (
            <div className="user-chip">
              <span className="user-chip-avatar">{currentUser.email.charAt(0).toUpperCase()}</span>
              <span>
                {currentUser.email}
                <span className="user-chip-role">{currentUser.role === 'ADMIN' ? '管理者' : '一般ユーザー'}</span>
              </span>
            </div>
          )}
          {isAuthenticated && (
            <button type="button" className="logout-btn" data-testid="app-layout-nav-logout" onClick={handleLogout}>
              ログアウト
            </button>
          )}
        </div>
      </header>
      {isAuthenticated && (
        <nav className="app-nav" data-testid="app-layout-nav">
          <div className="nav-group">
            <a
              href="/master-data"
              className={isActive('/master-data') ? 'is-active' : undefined}
              data-testid="app-layout-nav-master-data"
            >
              マスタデータ
            </a>
            <a
              href="/query-builder"
              className={isActive('/query-builder') ? 'is-active' : undefined}
              data-testid="app-layout-nav-query-builder"
            >
              クエリビルダー
            </a>
            <a
              href="/saved-queries"
              className={isActive('/saved-queries') ? 'is-active' : undefined}
              data-testid="app-layout-nav-saved-queries"
            >
              保存クエリ
            </a>
            <a
              href="/query-execution"
              className={isActive('/query-execution') ? 'is-active' : undefined}
              data-testid="app-layout-nav-query-execution"
            >
              クエリ実行
            </a>
            <a
              href="/query-history"
              className={isActive('/query-history') ? 'is-active' : undefined}
              data-testid="app-layout-nav-query-history"
            >
              クエリ履歴
            </a>
          </div>
          {currentUser?.role === 'ADMIN' && (
            <>
              <div className="nav-divider" />
              <div className="nav-group nav-group-admin">
                <span className="nav-group-tag">管理</span>
                <a
                  href="/admin/pending-users"
                  className={isActive('/admin/pending-users') ? 'is-active' : undefined}
                  data-testid="app-layout-nav-pending-users"
                >
                  承認待ちユーザー
                </a>
                <a
                  href="/admin/audit-logs"
                  className={isActive('/admin/audit-logs') ? 'is-active' : undefined}
                  data-testid="app-layout-nav-audit-logs"
                >
                  監査ログ
                </a>
                <a
                  href="/admin/rdbms-connections"
                  className={isActive('/admin/rdbms-connections') ? 'is-active' : undefined}
                  data-testid="app-layout-nav-rdbms-connections"
                >
                  RDBMS接続管理
                </a>
                <a
                  href="/admin/groups"
                  className={isActive('/admin/groups') ? 'is-active' : undefined}
                  data-testid="app-layout-nav-groups"
                >
                  グループ管理
                </a>
                <a
                  href="/admin/permissions"
                  className={isActive('/admin/permissions') ? 'is-active' : undefined}
                  data-testid="app-layout-nav-permissions"
                >
                  権限設定
                </a>
              </div>
            </>
          )}
        </nav>
      )}
      <main className="app-layout-main">{children}</main>
    </div>
  )
}