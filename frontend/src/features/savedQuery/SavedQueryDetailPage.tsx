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

import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { useAuth } from '../../hooks/useAuth'
import { getQuery, retireQuery, updateQuery } from './api'
import type { SavedQueryDetail, Visibility } from './types'

export function SavedQueryDetailPage() {
  const navigate = useNavigate()
  const params = useParams<{ id: string }>()
  const savedQueryId = Number(params.id)
  const { currentUser } = useAuth()

  const [savedQuery, setSavedQuery] = useState<SavedQueryDetail | null>(null)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [sql, setSql] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE')
  const [confirmingRetire, setConfirmingRetire] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = () => {
    getQuery(savedQueryId).then((detail) => {
      setSavedQuery(detail)
      setName(detail.name)
      setSql(detail.sql)
      setVisibility(detail.visibility)
    })
  }

  useEffect(reload, [savedQueryId])

  const isOwner = savedQuery !== null && currentUser !== null && savedQuery.ownerId === currentUser.id

  const handleUpdate = async () => {
    try {
      await updateQuery(savedQueryId, name, sql, visibility)
      setEditing(false)
      setError(null)
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '更新に失敗しました。')
    }
  }

  const handleRetire = async () => {
    setConfirmingRetire(false)
    try {
      await retireQuery(savedQueryId)
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '廃止に失敗しました。')
    }
  }

  if (savedQuery === null) {
    return <p>読み込み中...</p>
  }

  return (
    <div className="saved-query-detail-page" data-testid="saved-query-detail-page">
      <h1>{savedQuery.name}</h1>

      {editing ? (
        <>
          <label>
            名前
            <input
              type="text"
              data-testid="saved-query-detail-page-name-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            SQL
            <textarea
              data-testid="saved-query-detail-page-sql-textarea"
              value={sql}
              onChange={(e) => setSql(e.target.value)}
            />
          </label>
          <label>
            公開範囲
            <select
              data-testid="saved-query-detail-page-visibility-select"
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as Visibility)}
            >
              <option value="PRIVATE">非公開</option>
              <option value="PUBLIC">公開</option>
            </select>
          </label>
          <button
            type="button"
            className="btn-primary"
            data-testid="saved-query-detail-page-save-button"
            onClick={handleUpdate}
          >
            保存
          </button>
          <button type="button" data-testid="saved-query-detail-page-cancel-button" onClick={() => setEditing(false)}>
            キャンセル
          </button>
        </>
      ) : (
        <>
          <pre data-testid="saved-query-detail-page-sql">{savedQuery.sql}</pre>
          <p>公開範囲: {savedQuery.visibility === 'PUBLIC' ? '公開' : '非公開'}</p>
          <p>実行回数: {savedQuery.executionCount}</p>
          {savedQuery.retired && <p data-testid="saved-query-detail-page-retired-badge">廃止済み</p>}

          <button
            type="button"
            data-testid="saved-query-detail-page-execute-button"
            onClick={() => navigate(`/query-execution?savedQueryId=${savedQuery.id}`)}
          >
            実行
          </button>
          <button
            type="button"
            data-testid="saved-query-detail-page-edit-button"
            disabled={!isOwner || savedQuery.retired}
            onClick={() => setEditing(true)}
          >
            編集
          </button>
          <button
            type="button"
            data-testid="saved-query-detail-page-retire-button"
            disabled={!isOwner || savedQuery.retired}
            onClick={() => setConfirmingRetire(true)}
          >
            廃止
          </button>
        </>
      )}

      {error !== null && (
        <p data-testid="saved-query-detail-page-error" role="alert">
          {error}
        </p>
      )}

      {confirmingRetire && (
        <ConfirmDialog
          message={`「${savedQuery.name}」を廃止しますか？`}
          onConfirm={handleRetire}
          onCancel={() => setConfirmingRetire(false)}
        />
      )}
    </div>
  )
}