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

interface SchemaSelectorProps {
  schemas: string[]
  selected: string | null
  onSelect: (schema: string) => void
}

export function SchemaSelector({ schemas, selected, onSelect }: SchemaSelectorProps) {
  return (
    <select
      data-testid="schema-selector-select"
      value={selected ?? ''}
      onChange={(e) => onSelect(e.target.value)}
    >
      <option value="" disabled>
        スキーマを選択してください
      </option>
      {schemas.map((schema) => (
        <option key={schema} value={schema}>
          {schema}
        </option>
      ))}
    </select>
  )
}