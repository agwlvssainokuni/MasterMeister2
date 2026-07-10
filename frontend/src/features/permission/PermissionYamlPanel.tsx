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

import { type ChangeEvent, useState } from 'react'
import { exportPermissionsAsYaml, importPermissionsFromYaml } from './api'
import type { ImportResult } from './types'

interface PermissionYamlPanelProps {
  connectionId: number
}

export function PermissionYamlPanel({ connectionId }: PermissionYamlPanelProps) {
  const [exporting, setExporting] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)

  const handleExport = async () => {
    setExporting(true)
    try {
      const blob = await exportPermissionsAsYaml(connectionId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `permissions-${connectionId}.yaml`
      anchor.click()
      URL.revokeObjectURL(url)
    } finally {
      setExporting(false)
    }
  }

  const handleImport = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    setImporting(true)
    setImportResult(null)
    try {
      setImportResult(await importPermissionsFromYaml(connectionId, file))
    } finally {
      setImporting(false)
      event.target.value = ''
    }
  }

  return (
    <div className="permission-yaml-panel" data-testid="permission-yaml-panel">
      <button
        type="button"
        data-testid="permission-yaml-panel-export-button"
        onClick={handleExport}
        disabled={exporting}
      >
        エクスポート
      </button>
      <label>
        インポート
        <input
          type="file"
          accept=".yaml,.yml"
          data-testid="permission-yaml-panel-import-input"
          onChange={handleImport}
          disabled={importing}
        />
      </label>
      {importResult && (
        <p data-testid="permission-yaml-panel-import-result">
          {importResult.success ? 'インポートに成功しました' : `インポートに失敗しました: ${importResult.message}`}
        </p>
      )}
    </div>
  )
}