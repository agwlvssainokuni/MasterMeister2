import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../store/authStore'
import { login } from './api/authApi'
import { LoginPage } from './LoginPage'

vi.mock('./api/authApi', async () => {
  const actual = await vi.importActual<typeof import('./api/authApi')>('./api/authApi')
  return { ...actual, login: vi.fn() }
})

const loginMock = vi.mocked(login)

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin/pending-users" element={<div>Pending Users Page</div>} />
        <Route path="/" element={<div>Home Page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
    loginMock.mockReset()
  })

  it('navigates to the admin page and stores tokens on successful admin login', async () => {
    const payload = btoa(JSON.stringify({ sub: '1', role: 'ADMIN' }))
    loginMock.mockResolvedValue({ accessToken: `h.${payload}.s`, refreshToken: 'refresh-token' })
    renderLoginPage()

    fireEvent.change(screen.getByTestId('login-page-email-input'), { target: { value: 'admin@example.com' } })
    fireEvent.change(screen.getByTestId('login-page-password-input'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByTestId('login-page-submit-button'))

    expect(await screen.findByText('Pending Users Page')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe(`h.${payload}.s`)
    expect(useAuthStore.getState().refreshToken).toBe('refresh-token')
    expect(useAuthStore.getState().currentUser).toEqual({ id: 1, email: 'admin@example.com', role: 'ADMIN' })
  })

  it('navigates to the home page on successful non-admin login', async () => {
    const payload = btoa(JSON.stringify({ sub: '2', role: 'USER' }))
    loginMock.mockResolvedValue({ accessToken: `h.${payload}.s`, refreshToken: 'refresh-token' })
    renderLoginPage()

    fireEvent.change(screen.getByTestId('login-page-email-input'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByTestId('login-page-password-input'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByTestId('login-page-submit-button'))

    expect(await screen.findByText('Home Page')).toBeInTheDocument()
  })

  it('shows an error message when login fails', async () => {
    const { ApiError } = await import('../../api/apiClient')
    loginMock.mockRejectedValue(new ApiError(401, 'AUTHENTICATION_FAILED', 'Invalid email or password'))
    renderLoginPage()

    fireEvent.change(screen.getByTestId('login-page-email-input'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByTestId('login-page-password-input'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByTestId('login-page-submit-button'))

    expect(await screen.findByTestId('login-page-error-message')).toHaveTextContent('Invalid email or password')
  })
})