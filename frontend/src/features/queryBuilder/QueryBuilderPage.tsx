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
import { listSelectableConnections, listSelectableSchemas } from './api'
import { FromJoinTab } from './FromJoinTab'
import { SelectTab } from './SelectTab'
import type { ConnectionSummary, FromItem, JoinItem, SelectItem } from './types'

type TabKey = 'fromJoin' | 'select' | 'where' | 'groupBy' | 'having' | 'orderBy' | 'limitOffset'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'fromJoin', label: 'FROM/JOIN' },
  { key: 'select', label: 'SELECT' },
  { key: 'where', label: 'WHERE' },
  { key: 'groupBy', label: 'GROUP BY' },
  { key: 'having', label: 'HAVING' },
  { key: 'orderBy', label: 'ORDER BY' },
  { key: 'limitOffset', label: 'LIMIT/OFFSET' },
]

export function QueryBuilderPage() {
  const [connections, setConnections] = useState<ConnectionSummary[]>([])
  const [connectionId, setConnectionId] = useState<number | null>(null)
  const [schemas, setSchemas] = useState<string[]>([])
  const [schema, setSchema] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<TabKey>('fromJoin')
  const [fromItem, setFromItem] = useState<FromItem | null>(null)
  const [joinItems, setJoinItems] = useState<JoinItem[]>([])
  const [selectItems, setSelectItems] = useState<SelectItem[]>([])

  useEffect(() => {
    listSelectableConnections().then(setConnections)
  }, [])

  const handleSelectConnection = async (selectedConnectionId: number) => {
    setConnectionId(selectedConnectionId)
    setSchema(null)
    setFromItem(null)
    setJoinItems([])
    setSelectItems([])
    setSchemas(await listSelectableSchemas(selectedConnectionId))
  }

  const handleSelectSchema = (selectedSchema: string) => {
    setSchema(selectedSchema)
    setFromItem(null)
    setJoinItems([])
    setSelectItems([])
  }

  const handleChangeFromJoin = (nextFromItem: FromItem, nextJoinItems: JoinItem[]) => {
    setFromItem(nextFromItem)
    setJoinItems(nextJoinItems)
  }

  return (
    <div className="query-builder-page" data-testid="query-builder-page">
      <h1>クエリビルダー</h1>
      <label>
        対象接続
        <select
          data-testid="query-builder-page-connection-select"
          value={connectionId ?? ''}
          onChange={(e) => handleSelectConnection(Number(e.target.value))}
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
      {connectionId !== null && (
        <label>
          スキーマ
          <select
            data-testid="query-builder-page-schema-select"
            value={schema ?? ''}
            onChange={(e) => handleSelectSchema(e.target.value)}
          >
            <option value="" disabled>
              選択してください
            </option>
            {schemas.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      )}

      {connectionId !== null && schema !== null && (
        <>
          <nav className="query-builder-page-tabs">
            {TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                data-testid={`query-builder-page-tab-${tab.key}`}
                aria-current={activeTab === tab.key}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </nav>

          {activeTab === 'fromJoin' && (
            <FromJoinTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              onChange={handleChangeFromJoin}
            />
          )}
          {activeTab === 'select' && (
            <SelectTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              selectItems={selectItems}
              onChange={setSelectItems}
            />
          )}
          {activeTab !== 'fromJoin' && activeTab !== 'select' && <p>このタブは未実装です。</p>}
        </>
      )}
    </div>
  )
}