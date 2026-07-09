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
            <a href="/audit-logs" data-testid="app-layout-nav-audit-logs">
              監査ログ
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