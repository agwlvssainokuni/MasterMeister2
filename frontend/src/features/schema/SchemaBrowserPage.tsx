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
import { useParams } from 'react-router-dom'
import { getTableDetail, listSchemas, listTables } from './api/schemaApi'
import { SchemaSelector } from './SchemaSelector'
import { TableDetailPanel } from './TableDetailPanel'
import { TableList } from './TableList'
import type { TableDetail, TableMetadata } from './types'

export function SchemaBrowserPage() {
  const { connectionId: connectionIdParam } = useParams<{ connectionId: string }>()
  const connectionId = Number(connectionIdParam)

  const [schemas, setSchemas] = useState<string[]>([])
  const [selectedSchema, setSelectedSchema] = useState<string | null>(null)
  const [tables, setTables] = useState<TableMetadata[]>([])
  const [selectedTable, setSelectedTable] = useState<TableDetail | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    listSchemas(connectionId)
      .then(setSchemas)
      .finally(() => setLoading(false))
  }, [connectionId])

  const handleSelectSchema = async (schema: string) => {
    setSelectedSchema(schema)
    setSelectedTable(null)
    setTables(await listTables(connectionId, schema))
  }

  const handleSelectTable = async (table: TableMetadata) => {
    if (selectedSchema === null) {
      return
    }
    setSelectedTable(await getTableDetail(connectionId, selectedSchema, table.tableName))
  }

  return (
    <div className="schema-browser-page" data-testid="schema-browser-page">
      <h1>スキーマ参照</h1>
      {loading ? (
        <p>読み込み中...</p>
      ) : (
        <>
          <SchemaSelector schemas={schemas} selected={selectedSchema} onSelect={handleSelectSchema} />
          <TableList tables={tables} onSelect={handleSelectTable} />
          <TableDetailPanel detail={selectedTable} />
        </>
      )}
    </div>
  )
}