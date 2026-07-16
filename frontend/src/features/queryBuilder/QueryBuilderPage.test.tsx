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

import { StrictMode } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConnectionStore } from '../../store/connectionStore'
import { generateSql, listSelectableColumns, listSelectableSchemas, listSelectableTables, parseSql } from './api'
import { QueryBuilderPage } from './QueryBuilderPage'
import type { GeneratedSql, TableRef } from './types'

vi.mock('./api', () => ({
  listSelectableSchemas: vi.fn(),
  listSelectableTables: vi.fn(),
  listSelectableColumns: vi.fn(),
  generateSql: vi.fn(),
  parseSql: vi.fn(),
}))

const listSelectableSchemasMock = vi.mocked(listSelectableSchemas)
const listSelectableTablesMock = vi.mocked(listSelectableTables)
const listSelectableColumnsMock = vi.mocked(listSelectableColumns)
const generateSqlMock = vi.mocked(generateSql)
const parseSqlMock = vi.mocked(parseSql)

const table: TableRef = { schema: 'public', table: 'employees', comment: null }

function DestinationStub({ testId }: { testId: string }) {
  const [searchParams] = useSearchParams()
  return (
    <div data-testid={testId}>
      connectionId={searchParams.get('connectionId')}, schema={searchParams.get('schema')},
      rawSql={searchParams.get('rawSql')}
    </div>
  )
}

function renderPage(initialEntry = '/query-builder') {
  // StrictModeでラップし、開発モードのeffect二重実行下でも挙動が壊れないことを検証する
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/query-builder" element={<QueryBuilderPage />} />
          <Route path="/saved-queries/new" element={<DestinationStub testId="saved-query-save-form-stub" />} />
          <Route path="/query-execution" element={<DestinationStub testId="query-execution-page-stub" />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  )
}

async function selectSchema() {
  await screen.findByTestId('query-builder-page-schema-select')
  fireEvent.change(screen.getByTestId('query-builder-page-schema-select'), { target: { value: 'public' } })
}

describe('QueryBuilderPage', () => {
  beforeEach(() => {
    listSelectableSchemasMock.mockReset()
    listSelectableTablesMock.mockReset()
    listSelectableColumnsMock.mockReset()
    generateSqlMock.mockReset()
    parseSqlMock.mockReset()
    listSelectableSchemasMock.mockResolvedValue(['public'])
    listSelectableTablesMock.mockResolvedValue([table])
    listSelectableColumnsMock.mockResolvedValue([])
    useConnectionStore.setState({ connectionId: null, connections: [] })
  })

  it('shows a message when connectionId is not set in the global connection context', () => {
    renderPage()

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
    expect(listSelectableSchemasMock).not.toHaveBeenCalled()
  })

  it('loads schemas for the connectionId from the global connection context and hides the tabs until a schema is chosen', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })

    renderPage()

    expect(await screen.findByTestId('query-builder-page-schema-select')).toBeInTheDocument()
    expect(listSelectableSchemasMock).toHaveBeenCalledWith(1)
    expect(screen.queryByTestId('from-join-tab')).not.toBeInTheDocument()
  })

  it('prefills the schema from the schema URL query parameter on initial mount', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })

    renderPage('/query-builder?schema=public')

    expect(await screen.findByTestId('from-join-tab')).toBeInTheDocument()
  })

  it('resets the schema and in-progress model when the global connectionId changes after mount', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await selectSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    expect(screen.getByTestId('generated-sql-panel')).toBeInTheDocument()

    useConnectionStore.setState({ connectionId: 2, connections: [] })

    await waitFor(() => expect(listSelectableSchemasMock).toHaveBeenCalledWith(2))
    expect(screen.queryByTestId('from-join-tab')).not.toBeInTheDocument()
    expect(screen.queryByTestId('generated-sql-panel')).not.toBeInTheDocument()
  })

  it('shows the tab navigation and the FromJoin tab by default once a schema is chosen', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()

    await selectSchema()

    expect(screen.getByTestId('from-join-tab')).toBeInTheDocument()
  })

  it('switches to the SELECT tab content when its nav button is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await selectSchema()

    fireEvent.click(screen.getByTestId('query-builder-page-tab-select'))

    expect(screen.getByTestId('select-tab')).toBeInTheDocument()
    expect(screen.queryByTestId('from-join-tab')).not.toBeInTheDocument()
  })

  it('does not show the generated SQL panel until a base table has been selected', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await selectSchema()

    expect(screen.queryByTestId('generated-sql-panel')).not.toBeInTheDocument()
  })

  it('shows the generated SQL panel once a base table is selected in FromJoinTab', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await selectSchema()
    await screen.findByText('employees')

    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    expect(screen.getByTestId('generated-sql-panel')).toBeInTheDocument()
  })

  it('generates SQL and displays it when the generate button is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))

    expect(await screen.findByTestId('generated-sql-panel-sql')).toHaveTextContent(generated.sql)
  })

  it('navigates to the saved-query save form with connectionId/schema/rawSql when "保存" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))
    await screen.findByTestId('generated-sql-panel-sql')

    fireEvent.click(screen.getByText('保存'))

    expect(await screen.findByTestId('saved-query-save-form-stub')).toHaveTextContent(
      `connectionId=1, schema=public, rawSql=${generated.sql}`,
    )
  })

  it('navigates to the query execution page with connectionId/schema/rawSql when "実行" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))
    await screen.findByTestId('generated-sql-panel-sql')

    fireEvent.click(screen.getByText('実行'))

    expect(await screen.findByTestId('query-execution-page-stub')).toHaveTextContent(
      `connectionId=1, schema=public, rawSql=${generated.sql}`,
    )
  })
})