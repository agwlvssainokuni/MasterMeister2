import { type FormEvent, useState } from 'react'
import { requestRegistration } from './api/userRegistrationApi'

export function RegistrationRequestPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      await requestRegistration(email)
    } catch {
      // 列挙攻撃対策のため、失敗時も呼び出し元には伝播させず同一の完了表示にする（business-rules.md 1.1）
    } finally {
      setSubmitting(false)
      setSubmitted(true)
    }
  }

  if (submitted) {
    return (
      <div className="registration-request-page" data-testid="registration-request-page">
        <p data-testid="registration-request-page-success-message">
          確認メールを送信しました（該当するメールアドレスの場合）
        </p>
      </div>
    )
  }

  return (
    <div className="registration-request-page" data-testid="registration-request-page">
      <h1>ユーザ登録申請</h1>
      <form onSubmit={handleSubmit}>
        <label>
          メールアドレス
          <input
            type="email"
            data-testid="registration-request-page-email-input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <button type="submit" data-testid="registration-request-page-submit-button" disabled={submitting}>
          送信
        </button>
      </form>
    </div>
  )
}