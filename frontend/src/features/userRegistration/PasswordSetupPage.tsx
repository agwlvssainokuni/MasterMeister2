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
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { completeRegistration } from './api/userRegistrationApi'

export function PasswordSetupPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  // 明示的な事前チェックAPIは持たず、completeRegistration呼び出し自体の成否で判定する
  // （frontend-components.md）。URLにtokenパラメータが無い場合のみ即座に無効と判定する。
  const [tokenStatus, setTokenStatus] = useState<'valid' | 'invalid'>(token ? 'valid' : 'invalid')
  const [error, setError] = useState<string | null>(null)
  const [completed, setCompleted] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)

    if (password !== confirmPassword) {
      setError('パスワードが一致しません')
      return
    }

    setSubmitting(true)
    try {
      await completeRegistration(token, password)
      setCompleted(true)
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'TOKEN_EXPIRED' || e.code === 'TOKEN_NOT_FOUND')) {
        setTokenStatus('invalid')
      } else {
        setError(e instanceof ApiError ? e.message : '予期しないエラーが発生しました')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (completed) {
    return (
      <div className="password-setup-page" data-testid="password-setup-page">
        <p data-testid="password-setup-page-success-message">登録完了、管理者の承認をお待ちください</p>
      </div>
    )
  }

  if (tokenStatus === 'invalid') {
    return (
      <div className="password-setup-page" data-testid="password-setup-page">
        <p data-testid="password-setup-page-error-message" role="alert">
          このリンクは無効です。再度ユーザ登録申請を行ってください。
        </p>
        <Link to="/register">再申請はこちら</Link>
      </div>
    )
  }

  return (
    <div className="password-setup-page" data-testid="password-setup-page">
      <h1>パスワード設定</h1>
      <form onSubmit={handleSubmit}>
        <label>
          パスワード
          <input
            type="password"
            data-testid="password-setup-page-password-input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <label>
          パスワード（確認）
          <input
            type="password"
            data-testid="password-setup-page-confirm-password-input"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
        </label>
        {error && (
          <p data-testid="password-setup-page-error-message" role="alert">
            {error}
          </p>
        )}
        <button type="submit" data-testid="password-setup-page-submit-button" disabled={submitting}>
          設定する
        </button>
      </form>
    </div>
  )
}