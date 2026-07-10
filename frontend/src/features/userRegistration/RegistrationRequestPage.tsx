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

import { type FormEvent, useState } from 'react'
import { requestRegistration } from './api'

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