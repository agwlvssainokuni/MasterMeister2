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
import { GroupByOrderByTab } from './GroupByOrderByTab'
import { LimitOffsetTab } from './LimitOffsetTab'
import { SelectTab } from './SelectTab'
import { WhereHavingTab } from './WhereHavingTab'
import type { Condition, ConnectionSummary, FromItem, JoinItem, OrderByItem, SelectItem } from './types'

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
  const [whereConditions, setWhereConditions] = useState<Condition[]>([])
  const [groupByColumns, setGroupByColumns] = useState<string[]>([])
  const [havingConditions, setHavingConditions] = useState<Condition[]>([])
  const [orderByItems, setOrderByItems] = useState<OrderByItem[]>([])
  const [limit, setLimit] = useState<number | null>(null)
  const [offset, setOffset] = useState<number | null>(null)

  useEffect(() => {
    listSelectableConnections().then(setConnections)
  }, [])

  const handleSelectConnection = async (selectedConnectionId: number) => {
    setConnectionId(selectedConnectionId)
    setSchema(null)
    resetModel()
    setSchemas(await listSelectableSchemas(selectedConnectionId))
  }

  const handleSelectSchema = (selectedSchema: string) => {
    setSchema(selectedSchema)
    resetModel()
  }

  const resetModel = () => {
    setFromItem(null)
    setJoinItems([])
    setSelectItems([])
    setWhereConditions([])
    setGroupByColumns([])
    setHavingConditions([])
    setOrderByItems([])
    setLimit(null)
    setOffset(null)
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
          {activeTab === 'where' && (
            <WhereHavingTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              target="where"
              conditions={whereConditions}
              onChange={setWhereConditions}
            />
          )}
          {activeTab === 'having' && (
            <WhereHavingTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              target="having"
              conditions={havingConditions}
              onChange={setHavingConditions}
            />
          )}
          {activeTab === 'groupBy' && (
            <GroupByOrderByTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              target="groupBy"
              groupByColumns={groupByColumns}
              onChange={(value) => setGroupByColumns(value as string[])}
            />
          )}
          {activeTab === 'orderBy' && (
            <GroupByOrderByTab
              connectionId={connectionId}
              schema={schema}
              fromItem={fromItem}
              joinItems={joinItems}
              target="orderBy"
              orderByItems={orderByItems}
              onChange={(value) => setOrderByItems(value as OrderByItem[])}
            />
          )}
          {activeTab === 'limitOffset' && (
            <LimitOffsetTab
              limit={limit}
              offset={offset}
              onChange={(nextLimit, nextOffset) => {
                setLimit(nextLimit)
                setOffset(nextOffset)
              }}
            />
          )}
        </>
      )}
    </div>
  )
}