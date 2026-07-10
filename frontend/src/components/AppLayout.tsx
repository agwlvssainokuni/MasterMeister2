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
import { useAuth } from '../hooks/useAuth'

interface AppLayoutProps {
  children: ReactNode
}

export function AppLayout({ children }: AppLayoutProps) {
  const { currentUser, isAuthenticated, logout } = useAuth()

  return (
    <div className="app-layout">
      <header className="app-layout-header">
        <nav data-testid="app-layout-nav">
          <a href="/" data-testid="app-layout-nav-home">
            MasterMeister
          </a>
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
          {isAuthenticated && (
            <button type="button" data-testid="app-layout-nav-logout" onClick={logout}>
              ログアウト
            </button>
          )}
        </nav>
      </header>
      <main className="app-layout-main">{children}</main>
    </div>
  )
}