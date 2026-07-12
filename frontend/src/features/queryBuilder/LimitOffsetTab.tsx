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

interface LimitOffsetTabProps {
  limit: number | null
  offset: number | null
  onChange: (limit: number | null, offset: number | null) => void
}

export function LimitOffsetTab({ limit, offset, onChange }: LimitOffsetTabProps) {
  const parseNumberOrNull = (value: string): number | null => (value === '' ? null : Number(value))

  return (
    <div className="limit-offset-tab" data-testid="limit-offset-tab">
      <fieldset>
        <legend>LIMIT / OFFSET</legend>
        <label>
          件数（LIMIT）
          <input
            type="number"
            min={0}
            data-testid="limit-offset-tab-limit-input"
            value={limit ?? ''}
            onChange={(e) => onChange(parseNumberOrNull(e.target.value), offset)}
          />
        </label>
        <label>
          オフセット（OFFSET）
          <input
            type="number"
            min={0}
            data-testid="limit-offset-tab-offset-input"
            value={offset ?? ''}
            onChange={(e) => onChange(limit, parseNumberOrNull(e.target.value))}
          />
        </label>
      </fieldset>
    </div>
  )
}