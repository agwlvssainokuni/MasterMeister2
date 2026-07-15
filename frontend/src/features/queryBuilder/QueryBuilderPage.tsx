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

import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/apiClient'
import { useConnection } from '../../hooks/useConnection'
import { generateSql, listSelectableSchemas } from './api'
import { FromJoinTab } from './FromJoinTab'
import { GeneratedSqlPanel } from './GeneratedSqlPanel'
import { GroupByOrderByTab } from './GroupByOrderByTab'
import { LimitOffsetTab } from './LimitOffsetTab'
import { SelectTab } from './SelectTab'
import { SqlReverseParsePanel } from './SqlReverseParsePanel'
import { WhereHavingTab } from './WhereHavingTab'
import type {
  Condition,
  FromItem,
  GeneratedSql,
  JoinItem,
  OrderByItem,
  QueryBuilderModel,
  SelectItem,
} from './types'

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
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { connectionId } = useConnection()
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
  const [generatedSql, setGeneratedSql] = useState<GeneratedSql | null>(null)
  const [generateError, setGenerateError] = useState<string | null>(null)
  const hasInitializedConnection = useRef(false)

  useEffect(() => {
    if (connectionId === null) {
      setSchemas([])
      return
    }
    listSelectableSchemas(connectionId).then(setSchemas)
    if (!hasInitializedConnection.current) {
      hasInitializedConnection.current = true
      const urlSchema = searchParams.get('schema')
      if (urlSchema !== null) {
        setSchema(urlSchema)
      }
      return
    }
    setSchema(null)
    resetModel()
    // 接続切替時のスキーマ/組み立て中モデルのリセット（stories.md CHG-2、U1から委譲された責務）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionId])

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
    setGeneratedSql(null)
    setGenerateError(null)
  }

  const handleChangeFromJoin = (nextFromItem: FromItem, nextJoinItems: JoinItem[]) => {
    setFromItem(nextFromItem)
    setJoinItems(nextJoinItems)
  }

  const handleApplyParsedModel = async (model: QueryBuilderModel) => {
    if (connectionId !== null && !schemas.includes(model.fromItem.schema)) {
      setSchemas(await listSelectableSchemas(connectionId))
    }
    setSchema(model.fromItem.schema)
    setFromItem(model.fromItem)
    setJoinItems(model.joinItems)
    setSelectItems(model.selectItems)
    setWhereConditions(model.whereConditions)
    setGroupByColumns(model.groupByColumns)
    setHavingConditions(model.havingConditions)
    setOrderByItems(model.orderByItems)
    setLimit(model.limit)
    setOffset(model.offset)
    setGeneratedSql(null)
    setGenerateError(null)
    setActiveTab('fromJoin')
  }

  const handleGenerate = async () => {
    if (connectionId === null || fromItem === null) {
      return
    }
    const model: QueryBuilderModel = {
      selectItems,
      fromItem,
      joinItems,
      whereConditions,
      groupByColumns,
      havingConditions,
      orderByItems,
      limit,
      offset,
    }
    try {
      setGeneratedSql(await generateSql(connectionId, model))
      setGenerateError(null)
    } catch (e) {
      setGeneratedSql(null)
      setGenerateError(e instanceof ApiError ? e.message : 'SQL生成に失敗しました。')
    }
  }

  // GeneratedSql.paramsは値ではなく構造の引き継ぎ方針のため渡さない（business-rules.md 6節）。
  // connectionId（グローバル接続コンテキスト）とschema（画面内選択中の値）は遷移先
  // （savedQuery/queryExecution）が保存・実行のために必須とするため、あわせて引き継ぐ
  // （2026-07-15変更要求、GEN-8改訂AC）。
  const handleNavigateToSave = (sql: GeneratedSql) => {
    navigate(`/saved-queries/new?connectionId=${connectionId}&schema=${schema}&rawSql=${encodeURIComponent(sql.sql)}`)
  }

  const handleNavigateToExecute = (sql: GeneratedSql) => {
    navigate(`/query-execution?connectionId=${connectionId}&schema=${schema}&rawSql=${encodeURIComponent(sql.sql)}`)
  }

  return (
    <div className="query-builder-page" data-testid="query-builder-page">
      <h1>クエリビルダー</h1>
      {connectionId === null ? (
        <p>接続が指定されていません。</p>
      ) : (
        <>
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

          <SqlReverseParsePanel
            connectionId={connectionId}
            initialRawSql={searchParams.get('rawSql') ?? undefined}
            onApply={handleApplyParsedModel}
          />

          {schema !== null && (
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

          {fromItem !== null && (
            <GeneratedSqlPanel
              model={{
                selectItems,
                fromItem,
                joinItems,
                whereConditions,
                groupByColumns,
                havingConditions,
                orderByItems,
                limit,
                offset,
              }}
              generatedSql={generatedSql}
              error={generateError}
              onGenerate={handleGenerate}
              onNavigateToSave={handleNavigateToSave}
              onNavigateToExecute={handleNavigateToExecute}
            />
          )}
            </>
          )}
        </>
      )}
    </div>
  )
}