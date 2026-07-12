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

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { GeneratedSqlPanel } from './GeneratedSqlPanel'
import type { GeneratedSql, QueryBuilderModel } from './types'

const model: QueryBuilderModel = {
  selectItems: [],
  fromItem: { schema: 'public', table: 'employees', alias: 't0' },
  joinItems: [],
  whereConditions: [],
  groupByColumns: [],
  havingConditions: [],
  orderByItems: [],
  limit: null,
  offset: null,
}

const generatedSql: GeneratedSql = {
  sql: 'SELECT "t0"."id" FROM "employees" AS "t0" WHERE "t0"."id" = :param1',
  params: { param1: 42 },
}

describe('GeneratedSqlPanel', () => {
  it('calls onGenerate when the SQL generation button is clicked', () => {
    const onGenerate = vi.fn()
    render(<GeneratedSqlPanel model={model} generatedSql={null} onGenerate={onGenerate} />)

    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))

    expect(onGenerate).toHaveBeenCalled()
  })

  it('shows nothing for the result when generatedSql is null', () => {
    render(<GeneratedSqlPanel model={model} generatedSql={null} onGenerate={vi.fn()} />)

    expect(screen.queryByTestId('generated-sql-panel-result')).not.toBeInTheDocument()
  })

  it('displays the generated SQL and its parameters', () => {
    render(<GeneratedSqlPanel model={model} generatedSql={generatedSql} onGenerate={vi.fn()} />)

    expect(screen.getByTestId('generated-sql-panel-sql')).toHaveTextContent(generatedSql.sql)
    expect(screen.getByText('param1')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('displays an error message when provided', () => {
    render(<GeneratedSqlPanel model={model} generatedSql={null} error="件数が上限を超えています" onGenerate={vi.fn()} />)

    expect(screen.getByTestId('generated-sql-panel-error')).toHaveTextContent('件数が上限を超えています')
  })

  it('copies the generated SQL to the clipboard when the copy button is clicked', () => {
    const writeText = vi.fn()
    Object.assign(navigator, { clipboard: { writeText } })
    render(<GeneratedSqlPanel model={model} generatedSql={generatedSql} onGenerate={vi.fn()} />)

    fireEvent.click(screen.getByText('コピー'))

    expect(writeText).toHaveBeenCalledWith(generatedSql.sql)
  })

  it('disables save/execute buttons when their handlers are not supplied, and enables them otherwise', () => {
    const onNavigateToSave = vi.fn()
    render(
      <GeneratedSqlPanel
        model={model}
        generatedSql={generatedSql}
        onGenerate={vi.fn()}
        onNavigateToSave={onNavigateToSave}
      />,
    )

    expect(screen.getByText('実行')).toBeDisabled()
    expect(screen.getByText('保存')).not.toBeDisabled()

    fireEvent.click(screen.getByText('保存'))
    expect(onNavigateToSave).toHaveBeenCalledWith(generatedSql)
  })
})