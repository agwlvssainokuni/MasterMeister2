import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { AppRouter } from './AppRouter'

function renderAt(path: string) {
  window.history.pushState({}, '', path)
  return render(<AppRouter />)
}

describe('AppRouter', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
  })

  it('renders the login page without the AppLayout navigation on a public route', () => {
    renderAt('/login')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav')).not.toBeInTheDocument()
  })

  it('renders the registration request page without the AppLayout navigation on a public route', () => {
    renderAt('/register')

    expect(screen.getByTestId('registration-request-page')).toBeInTheDocument()
    expect(screen.queryByTestId('app-layout-nav')).not.toBeInTheDocument()
  })

  it('redirects to /login when accessing a protected route while unauthenticated', () => {
    renderAt('/admin/audit-logs')

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('renders the AppLayout navigation for authenticated routes', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
      refreshToken: 'a-refresh-token',
    })

    renderAt('/')

    expect(screen.getByTestId('app-layout-nav')).toBeInTheDocument()
  })
})