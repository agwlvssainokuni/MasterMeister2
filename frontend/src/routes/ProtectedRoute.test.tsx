import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { ProtectedRoute } from './ProtectedRoute'

function renderProtectedRoute(requiredRole?: 'ADMIN' | 'USER') {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route
          path="/protected"
          element={
            <ProtectedRoute requiredRole={requiredRole}>
              <div>Protected Content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/" element={<div>Home Page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null })
  })

  it('redirects to /login when the user is not authenticated', () => {
    renderProtectedRoute()

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('redirects to / when the current user does not have the required role', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
    })

    renderProtectedRoute('ADMIN')

    expect(screen.getByText('Home Page')).toBeInTheDocument()
  })

  it('renders the children when authenticated and the role matches', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'admin@example.com', role: 'ADMIN' },
      token: 'a-token',
    })

    renderProtectedRoute('ADMIN')

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('renders the children when authenticated and no role is required', () => {
    useAuthStore.setState({
      currentUser: { id: 1, email: 'user@example.com', role: 'USER' },
      token: 'a-token',
    })

    renderProtectedRoute()

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })
})