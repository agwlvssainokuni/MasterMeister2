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

import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { useConnection } from '../../hooks/useConnection'
import { saveQuery } from './api'
import type { Visibility } from './types'

export function SavedQuerySaveForm() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { connectionId } = useConnection()

  const [name, setName] = useState('')
  const [sql, setSql] = useState(searchParams.get('rawSql') ?? '')
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async () => {
    if (connectionId === null) {
      return
    }
    try {
      const savedQueryId = await saveQuery(connectionId, name, sql, visibility)
      navigate(`/saved-queries/${savedQueryId}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存に失敗しました。')
    }
  }

  return (
    <div className="saved-query-save-form" data-testid="saved-query-save-form">
      <h1>クエリの保存</h1>

      {connectionId === null ? (
        <p>接続が指定されていません。</p>
      ) : (
        <>
          <label>
            名前
            <input
              type="text"
              data-testid="saved-query-save-form-name-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            SQL
            <textarea
              data-testid="saved-query-save-form-sql-textarea"
              value={sql}
              onChange={(e) => setSql(e.target.value)}
            />
          </label>
          <label>
            公開範囲
            <select
              data-testid="saved-query-save-form-visibility-select"
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as Visibility)}
            >
              <option value="PRIVATE">非公開</option>
              <option value="PUBLIC">公開</option>
            </select>
          </label>

          {error !== null && (
            <p data-testid="saved-query-save-form-error" role="alert">
              {error}
            </p>
          )}

          <button
            type="button"
            className="btn-primary"
            data-testid="saved-query-save-form-submit-button"
            onClick={handleSubmit}
          >
            保存
          </button>
        </>
      )}
    </div>
  )
}