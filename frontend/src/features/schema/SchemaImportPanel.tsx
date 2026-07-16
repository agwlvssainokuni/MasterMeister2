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
import { importSchema } from './api'
import type { SchemaImportResult } from './types'

interface SchemaImportPanelProps {
  connectionId: number
  onClose: () => void
}

export function SchemaImportPanel({ connectionId, onClose }: SchemaImportPanelProps) {
  const [importing, setImporting] = useState(false)
  const [result, setResult] = useState<SchemaImportResult | null>(null)

  const handleImport = async () => {
    setImporting(true)
    setResult(null)
    try {
      setResult(await importSchema(connectionId))
    } catch {
      setResult({ success: false, tableCount: 0, message: '取り込みに失敗しました' })
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="schema-import-panel" data-testid="schema-import-panel">
      <h2>スキーマ取り込み</h2>
      <button
        type="button"
        className="btn-primary"
        data-testid="schema-import-panel-import-button"
        onClick={handleImport}
        disabled={importing}
      >
        取り込み実行
      </button>
      {result && (
        <p data-testid="schema-import-panel-result-message">
          {result.success ? `取り込みに成功しました（${result.tableCount}件）` : `取り込みに失敗しました: ${result.message}`}
        </p>
      )}
      <button type="button" data-testid="schema-import-panel-close-button" onClick={onClose}>
        閉じる
      </button>
    </div>
  )
}