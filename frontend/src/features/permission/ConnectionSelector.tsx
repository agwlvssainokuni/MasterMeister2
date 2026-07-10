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
import { listConnections } from '../rdbmsConnection/api'
import type { ConnectionSummary } from '../rdbmsConnection/types'

interface ConnectionSelectorProps {
  selected: number | null
  onSelect: (connectionId: number) => void
}

export function ConnectionSelector({ selected, onSelect }: ConnectionSelectorProps) {
  const [connections, setConnections] = useState<ConnectionSummary[]>([])

  useEffect(() => {
    listConnections().then(setConnections)
  }, [])

  return (
    <label>
      対象接続
      <select
        data-testid="connection-selector-select"
        value={selected ?? ''}
        onChange={(e) => onSelect(Number(e.target.value))}
      >
        <option value="" disabled>
          選択してください
        </option>
        {connections.map((connection) => (
          <option key={connection.id} value={connection.id}>
            {connection.name}
          </option>
        ))}
      </select>
    </label>
  )
}