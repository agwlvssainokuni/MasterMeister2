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
import { listGroups } from '../group/api'
import type { GroupSummary } from '../group/types'
import type { PrincipalRef, PrincipalType } from './types'

interface PrincipalSelectorProps {
  selected: PrincipalRef | null
  onSelect: (principal: PrincipalRef) => void
}

export function PrincipalSelector({ selected, onSelect }: PrincipalSelectorProps) {
  const [principalType, setPrincipalType] = useState<PrincipalType>(selected?.principalType ?? 'USER')
  const [userIdInput, setUserIdInput] = useState('')
  const [groups, setGroups] = useState<GroupSummary[]>([])

  useEffect(() => {
    if (principalType === 'GROUP') {
      listGroups().then(setGroups)
    }
  }, [principalType])

  const handleUserIdSubmit = (event: FormEvent) => {
    event.preventDefault()
    onSelect({ principalType: 'USER', principalId: Number(userIdInput) })
  }

  return (
    <div className="principal-selector" data-testid="principal-selector">
      <div role="tablist">
        <button
          type="button"
          data-testid="principal-selector-user-tab"
          aria-pressed={principalType === 'USER'}
          onClick={() => setPrincipalType('USER')}
        >
          ユーザ
        </button>
        <button
          type="button"
          data-testid="principal-selector-group-tab"
          aria-pressed={principalType === 'GROUP'}
          onClick={() => setPrincipalType('GROUP')}
        >
          グループ
        </button>
      </div>
      {principalType === 'USER' ? (
        <form onSubmit={handleUserIdSubmit}>
          <label>
            ユーザID
            <input
              type="number"
              data-testid="principal-selector-user-id-input"
              value={userIdInput}
              onChange={(e) => setUserIdInput(e.target.value)}
              required
            />
          </label>
          <button type="submit" data-testid="principal-selector-user-select-button">
            選択
          </button>
        </form>
      ) : (
        <select
          data-testid="principal-selector-group-select"
          value={selected?.principalType === 'GROUP' ? selected.principalId : ''}
          onChange={(e) => onSelect({ principalType: 'GROUP', principalId: Number(e.target.value) })}
        >
          <option value="" disabled>
            選択してください
          </option>
          {groups.map((group) => (
            <option key={group.id} value={group.id}>
              {group.name}
            </option>
          ))}
        </select>
      )}
    </div>
  )
}