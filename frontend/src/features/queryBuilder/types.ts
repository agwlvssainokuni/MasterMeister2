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

export interface TableRef {
  schema: string
  table: string
  comment: string | null
}

export interface ColumnRef {
  columnName: string
  dataType: string
  nullable: boolean
}

export interface FromItem {
  schema: string
  table: string
  alias: string
}

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT'

export type AggregateFunction = 'NONE' | 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'

export type Operator = 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'LIKE' | 'IS_NULL' | 'IS_NOT_NULL'

export type SortDirection = 'ASC' | 'DESC'

export interface Condition {
  tableAlias: string
  columnName: string
  aggregateFunction: AggregateFunction
  operator: Operator
  value: unknown
}

export interface JoinItem {
  type: JoinType
  schema: string
  table: string
  alias: string
  onCondition: Condition
}

export interface SelectItem {
  tableAlias: string
  columnName: string
  aggregateFunction: AggregateFunction
  outputAlias: string | null
}

export interface OrderByItem {
  tableAlias: string
  columnName: string
  aggregateFunction: AggregateFunction
  direction: SortDirection
}

export interface QueryBuilderModel {
  selectItems: SelectItem[]
  fromItem: FromItem
  joinItems: JoinItem[]
  whereConditions: Condition[]
  groupByColumns: string[]
  havingConditions: Condition[]
  orderByItems: OrderByItem[]
  limit: number | null
  offset: number | null
}

export interface GeneratedSql {
  sql: string
  params: Record<string, unknown>
}

export interface ParseResult {
  fullyParsed: boolean
  model: QueryBuilderModel | null
  notice: string | null
}