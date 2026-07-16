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

import type { GeneratedSql, QueryBuilderModel } from './types'

interface GeneratedSqlPanelProps {
  model: QueryBuilderModel
  generatedSql: GeneratedSql | null
  error?: string | null
  onGenerate: () => void
  onNavigateToSave?: (sql: GeneratedSql) => void
  onNavigateToExecute?: (sql: GeneratedSql) => void
}

export function GeneratedSqlPanel({
  generatedSql,
  error,
  onGenerate,
  onNavigateToSave,
  onNavigateToExecute,
}: GeneratedSqlPanelProps) {
  const handleCopy = () => {
    if (generatedSql != null) {
      void navigator.clipboard.writeText(generatedSql.sql)
    }
  }

  return (
    <div className="generated-sql-panel" data-testid="generated-sql-panel">
      <button type="button" data-testid="generated-sql-panel-generate-button" onClick={onGenerate}>
        SQL生成
      </button>

      {error != null && (
        <p className="generated-sql-panel-error" data-testid="generated-sql-panel-error">
          {error}
        </p>
      )}

      {generatedSql != null && (
        <div className="generated-sql-panel-result" data-testid="generated-sql-panel-result">
          <pre data-testid="generated-sql-panel-sql">{generatedSql.sql}</pre>
          <button type="button" onClick={handleCopy}>
            コピー
          </button>
          {Object.keys(generatedSql.params).length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>パラメータ名</th>
                  <th>値</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(generatedSql.params).map(([key, value]) => (
                  <tr key={key}>
                    <td>{key}</td>
                    <td>{String(value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <button type="button" disabled={onNavigateToSave == null} onClick={() => onNavigateToSave?.(generatedSql)}>
            保存
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={onNavigateToExecute == null}
            onClick={() => onNavigateToExecute?.(generatedSql)}
          >
            実行
          </button>
        </div>
      )}
    </div>
  )
}