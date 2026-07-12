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
import { parseSql } from './api'
import type { ParseResult, QueryBuilderModel } from './types'

interface SqlReverseParsePanelProps {
  connectionId: number
  initialRawSql?: string
  onApply: (model: QueryBuilderModel) => void
}

export function SqlReverseParsePanel({ connectionId, initialRawSql, onApply }: SqlReverseParsePanelProps) {
  const [rawSql, setRawSql] = useState(initialRawSql ?? '')
  const [parseResult, setParseResult] = useState<ParseResult | null>(null)

  const handleParse = async (sqlToParse: string) => {
    const result = await parseSql(connectionId, sqlToParse)
    setParseResult(result)
    if (result.fullyParsed && result.model != null) {
      onApply(result.model)
    }
  }

  useEffect(() => {
    if (initialRawSql != null && initialRawSql !== '') {
      void handleParse(initialRawSql)
    }
    // マウント時のみ、URLクエリパラメータ経由の自動解析（GEN-9、U7からの遷移想定）を行う
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="sql-reverse-parse-panel" data-testid="sql-reverse-parse-panel">
      <fieldset>
        <legend>SQL貼り付けから反映</legend>
        <label>
          SQL
          <textarea
            data-testid="sql-reverse-parse-panel-raw-sql-input"
            value={rawSql}
            onChange={(e) => setRawSql(e.target.value)}
          />
        </label>
        <button
          type="button"
          data-testid="sql-reverse-parse-panel-parse-button"
          onClick={() => handleParse(rawSql)}
        >
          解析して反映
        </button>
        {parseResult != null && !parseResult.fullyParsed && (
          <p className="sql-reverse-parse-panel-notice" data-testid="sql-reverse-parse-panel-notice">
            {parseResult.notice}
          </p>
        )}
      </fieldset>
    </div>
  )
}