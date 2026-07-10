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
import { getTableDetail, listSchemas, listTables } from '../schema/api'
import type { ColumnDetail, TableMetadata } from '../schema/types'
import type { SchemaTreeNode } from './types'

interface PermissionTreeProps {
  connectionId: number
  selectedNode: SchemaTreeNode | null
  onSelectNode: (node: SchemaTreeNode) => void
}

export function PermissionTree({ connectionId, selectedNode, onSelectNode }: PermissionTreeProps) {
  const [schemas, setSchemas] = useState<string[]>([])
  const [tablesBySchema, setTablesBySchema] = useState<Record<string, TableMetadata[]>>({})
  const [columnsByTable, setColumnsByTable] = useState<Record<string, ColumnDetail[]>>({})
  const [expandedSchemas, setExpandedSchemas] = useState<Set<string>>(new Set())
  const [expandedTables, setExpandedTables] = useState<Set<string>>(new Set())

  useEffect(() => {
    listSchemas(connectionId).then(setSchemas)
    setExpandedSchemas(new Set())
    setExpandedTables(new Set())
    setTablesBySchema({})
    setColumnsByTable({})
  }, [connectionId])

  const isSelected = (node: SchemaTreeNode) =>
    selectedNode?.level === node.level &&
    selectedNode?.schema === node.schema &&
    selectedNode?.table === node.table &&
    selectedNode?.column === node.column

  const toggleSchema = async (schema: string) => {
    const next = new Set(expandedSchemas)
    if (next.has(schema)) {
      next.delete(schema)
    } else {
      next.add(schema)
      if (!tablesBySchema[schema]) {
        const tables = await listTables(connectionId, schema)
        setTablesBySchema((prev) => ({ ...prev, [schema]: tables }))
      }
    }
    setExpandedSchemas(next)
  }

  const toggleTable = async (schema: string, table: string) => {
    const key = `${schema}.${table}`
    const next = new Set(expandedTables)
    if (next.has(key)) {
      next.delete(key)
    } else {
      next.add(key)
      if (!columnsByTable[key]) {
        const detail = await getTableDetail(connectionId, schema, table)
        setColumnsByTable((prev) => ({ ...prev, [key]: detail.columns }))
      }
    }
    setExpandedTables(next)
  }

  return (
    <ul className="permission-tree" data-testid="permission-tree">
      {schemas.map((schema) => (
        <li key={schema}>
          <button type="button" data-testid="permission-tree-schema-toggle" onClick={() => toggleSchema(schema)}>
            {expandedSchemas.has(schema) ? '▼' : '▶'}
          </button>
          <button
            type="button"
            data-testid="permission-tree-schema-select"
            aria-pressed={isSelected({ level: 'schema', schema })}
            onClick={() => onSelectNode({ level: 'schema', schema })}
          >
            {schema}
          </button>
          {expandedSchemas.has(schema) && (
            <ul>
              {(tablesBySchema[schema] ?? []).map((table) => {
                const tableKey = `${schema}.${table.tableName}`
                return (
                  <li key={table.tableName}>
                    <button
                      type="button"
                      data-testid="permission-tree-table-toggle"
                      onClick={() => toggleTable(schema, table.tableName)}
                    >
                      {expandedTables.has(tableKey) ? '▼' : '▶'}
                    </button>
                    <button
                      type="button"
                      data-testid="permission-tree-table-select"
                      aria-pressed={isSelected({ level: 'table', schema, table: table.tableName })}
                      onClick={() => onSelectNode({ level: 'table', schema, table: table.tableName })}
                    >
                      {table.tableName}
                    </button>
                    {expandedTables.has(tableKey) && (
                      <ul>
                        {(columnsByTable[tableKey] ?? []).map((column) => (
                          <li key={column.columnName}>
                            <button
                              type="button"
                              data-testid="permission-tree-column-select"
                              aria-pressed={isSelected({
                                level: 'column',
                                schema,
                                table: table.tableName,
                                column: column.columnName,
                              })}
                              onClick={() =>
                                onSelectNode({
                                  level: 'column',
                                  schema,
                                  table: table.tableName,
                                  column: column.columnName,
                                })
                              }
                            >
                              {column.columnName}
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </li>
      ))}
    </ul>
  )
}