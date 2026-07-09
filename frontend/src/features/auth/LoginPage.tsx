import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { useAuth } from '../../hooks/useAuth'
import { decodeAccessToken, login } from './api/authApi'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { setTokens } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const { accessToken, refreshToken } = await login(email, password)
      const { userId, role } = decodeAccessToken(accessToken)
      setTokens({ id: userId, email, role }, accessToken, refreshToken)
      navigate(role === 'ADMIN' ? '/admin/pending-users' : '/', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '予期しないエラーが発生しました')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page" data-testid="login-page">
      <h1>ログイン</h1>
      <form onSubmit={handleSubmit}>
        <label>
          メールアドレス
          <input
            type="email"
            data-testid="login-page-email-input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          パスワード
          <input
            type="password"
            data-testid="login-page-password-input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && (
          <p data-testid="login-page-error-message" role="alert">
            {error}
          </p>
        )}
        <button type="submit" data-testid="login-page-submit-button" disabled={submitting}>
          ログイン
        </button>
      </form>
    </div>
  )
}