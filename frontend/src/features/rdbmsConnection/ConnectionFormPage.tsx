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

import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createConnection, getConnection, testConnection, updateConnection } from './api'
import type { ConnectionConfig, ConnectionTestResult, RdbmsType } from './types'

interface ConnectionFormPageProps {
  mode: 'create' | 'edit'
}

const RDBMS_TYPES: RdbmsType[] = ['MYSQL', 'MARIADB', 'POSTGRESQL', 'H2']

const emptyForm: ConnectionConfig = {
  name: '',
  rdbmsType: 'MYSQL',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  additionalParams: '',
}

export function ConnectionFormPage({ mode }: ConnectionFormPageProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const connectionId = mode === 'edit' && id ? Number(id) : null

  const [form, setForm] = useState<ConnectionConfig>(emptyForm)
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null)
  const [testing, setTesting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (connectionId === null) {
      return
    }
    getConnection(connectionId).then((detail) => {
      // 既存パスワードは復号値を返さないAPI仕様のため空欄で初期化する。
      // 空欄のまま保存すれば、RdbmsConnectionService.updateConnectionが既存の暗号化済み
      // パスワードを変更しない（frontend-components.md 47-50行）。
      setForm({
        name: detail.name,
        rdbmsType: detail.rdbmsType,
        host: detail.host,
        port: detail.port,
        databaseName: detail.databaseName,
        username: detail.username,
        password: '',
        additionalParams: detail.additionalParams ?? '',
      })
    })
  }, [connectionId])

  const updateField = <K extends keyof ConnectionConfig>(key: K, value: ConnectionConfig[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      setTestResult(await testConnection(form))
    } catch {
      setTestResult({ success: false, message: '接続テストに失敗しました' })
    } finally {
      setTesting(false)
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      if (mode === 'create') {
        await createConnection(form)
      } else if (connectionId !== null) {
        await updateConnection(connectionId, form)
      }
      navigate('/admin/rdbms-connections')
    } catch {
      setError('保存に失敗しました')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="connection-form-page" data-testid="connection-form-page">
      <h1>{mode === 'create' ? 'RDBMS接続の新規登録' : 'RDBMS接続の編集'}</h1>
      {error && <p role="alert">{error}</p>}
      <form onSubmit={handleSubmit}>
        <label>
          接続名
          <input
            type="text"
            data-testid="connection-form-page-name-input"
            value={form.name}
            onChange={(e) => updateField('name', e.target.value)}
            required
          />
        </label>
        <label>
          RDBMS種別
          <select
            data-testid="connection-form-page-rdbms-type-select"
            value={form.rdbmsType}
            onChange={(e) => updateField('rdbmsType', e.target.value as RdbmsType)}
          >
            {RDBMS_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>
        <label>
          ホスト
          <input
            type="text"
            data-testid="connection-form-page-host-input"
            value={form.host}
            onChange={(e) => updateField('host', e.target.value)}
            required
          />
        </label>
        <label>
          ポート
          <input
            type="number"
            data-testid="connection-form-page-port-input"
            value={form.port}
            onChange={(e) => updateField('port', Number(e.target.value))}
            required
          />
        </label>
        <label>
          データベース名
          <input
            type="text"
            data-testid="connection-form-page-database-name-input"
            value={form.databaseName}
            onChange={(e) => updateField('databaseName', e.target.value)}
            required
          />
        </label>
        <label>
          ユーザ名
          <input
            type="text"
            data-testid="connection-form-page-username-input"
            value={form.username}
            onChange={(e) => updateField('username', e.target.value)}
            required
          />
        </label>
        <label>
          パスワード{mode === 'edit' && '（変更する場合のみ入力）'}
          <input
            type="password"
            data-testid="connection-form-page-password-input"
            value={form.password}
            onChange={(e) => updateField('password', e.target.value)}
            placeholder={mode === 'edit' ? '変更しない場合は空欄のまま' : undefined}
            required={mode === 'create'}
          />
        </label>
        <label>
          追加パラメータ
          <input
            type="text"
            data-testid="connection-form-page-additional-params-input"
            value={form.additionalParams}
            onChange={(e) => updateField('additionalParams', e.target.value)}
          />
        </label>
        <button type="button" data-testid="connection-form-page-test-button" onClick={handleTest} disabled={testing}>
          接続テスト
        </button>
        {testResult && (
          <p data-testid="connection-form-page-test-result-message">
            {testResult.success ? '接続に成功しました' : `接続に失敗しました: ${testResult.message}`}
          </p>
        )}
        <button type="submit" data-testid="connection-form-page-submit-button" disabled={submitting}>
          保存
        </button>
      </form>
    </div>
  )
}