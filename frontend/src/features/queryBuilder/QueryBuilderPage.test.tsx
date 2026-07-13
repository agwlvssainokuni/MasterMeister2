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
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  generateSql,
  listSelectableColumns,
  listSelectableConnections,
  listSelectableSchemas,
  listSelectableTables,
  parseSql,
} from './api'
import { QueryBuilderPage } from './QueryBuilderPage'
import type { ConnectionSummary, GeneratedSql, TableRef } from './types'

vi.mock('./api', () => ({
  listSelectableConnections: vi.fn(),
  listSelectableSchemas: vi.fn(),
  listSelectableTables: vi.fn(),
  listSelectableColumns: vi.fn(),
  generateSql: vi.fn(),
  parseSql: vi.fn(),
}))

const listSelectableConnectionsMock = vi.mocked(listSelectableConnections)
const listSelectableSchemasMock = vi.mocked(listSelectableSchemas)
const listSelectableTablesMock = vi.mocked(listSelectableTables)
const listSelectableColumnsMock = vi.mocked(listSelectableColumns)
const generateSqlMock = vi.mocked(generateSql)
const parseSqlMock = vi.mocked(parseSql)

const connection: ConnectionSummary = {
  id: 1, name: 'conn-1', rdbmsType: 'POSTGRESQL', host: 'localhost', databaseName: 'db1',
}
const table: TableRef = { schema: 'public', table: 'employees', comment: null }

function DestinationStub({ testId }: { testId: string }) {
  const [searchParams] = useSearchParams()
  return (
    <div data-testid={testId}>
      connectionId={searchParams.get('connectionId')}, rawSql={searchParams.get('rawSql')}
    </div>
  )
}

function renderPage(initialEntry = '/query-builder') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/query-builder" element={<QueryBuilderPage />} />
        <Route path="/saved-queries/new" element={<DestinationStub testId="saved-query-save-form-stub" />} />
        <Route path="/query-execution" element={<DestinationStub testId="query-execution-page-stub" />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function selectConnectionAndSchema() {
  await screen.findByText('conn-1')
  fireEvent.change(screen.getByTestId('query-builder-page-connection-select'), { target: { value: '1' } })
  await screen.findByTestId('query-builder-page-schema-select')
  fireEvent.change(screen.getByTestId('query-builder-page-schema-select'), { target: { value: 'public' } })
}

describe('QueryBuilderPage', () => {
  beforeEach(() => {
    listSelectableConnectionsMock.mockReset()
    listSelectableSchemasMock.mockReset()
    listSelectableTablesMock.mockReset()
    listSelectableColumnsMock.mockReset()
    generateSqlMock.mockReset()
    parseSqlMock.mockReset()
    listSelectableConnectionsMock.mockResolvedValue([connection])
    listSelectableSchemasMock.mockResolvedValue(['public'])
    listSelectableTablesMock.mockResolvedValue([table])
    listSelectableColumnsMock.mockResolvedValue([])
  })

  it('lists accessible connections and hides the schema select until one is chosen', async () => {
    renderPage()

    await screen.findByText('conn-1')
    expect(screen.queryByTestId('query-builder-page-schema-select')).not.toBeInTheDocument()
  })

  it('shows the tab navigation and the FromJoin tab by default once a connection/schema are chosen', async () => {
    renderPage()

    await selectConnectionAndSchema()

    expect(screen.getByTestId('from-join-tab')).toBeInTheDocument()
  })

  it('switches to the SELECT tab content when its nav button is clicked', async () => {
    renderPage()
    await selectConnectionAndSchema()

    fireEvent.click(screen.getByTestId('query-builder-page-tab-select'))

    expect(screen.getByTestId('select-tab')).toBeInTheDocument()
    expect(screen.queryByTestId('from-join-tab')).not.toBeInTheDocument()
  })

  it('does not show the generated SQL panel until a base table has been selected', async () => {
    renderPage()
    await selectConnectionAndSchema()

    expect(screen.queryByTestId('generated-sql-panel')).not.toBeInTheDocument()
  })

  it('shows the generated SQL panel once a base table is selected in FromJoinTab', async () => {
    renderPage()
    await selectConnectionAndSchema()
    await screen.findByText('employees')

    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    expect(screen.getByTestId('generated-sql-panel')).toBeInTheDocument()
  })

  it('generates SQL and displays it when the generate button is clicked', async () => {
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectConnectionAndSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })

    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))

    expect(await screen.findByTestId('generated-sql-panel-sql')).toHaveTextContent(generated.sql)
  })

  it('navigates to the saved-query save form with connectionId/rawSql when "保存" is clicked', async () => {
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectConnectionAndSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))
    await screen.findByTestId('generated-sql-panel-sql')

    fireEvent.click(screen.getByText('保存'))

    expect(await screen.findByTestId('saved-query-save-form-stub')).toHaveTextContent(
      `connectionId=1, rawSql=${generated.sql}`,
    )
  })

  it('navigates to the query execution page with connectionId/rawSql when "実行" is clicked', async () => {
    const generated: GeneratedSql = { sql: 'SELECT "t0"."id" FROM "employees" AS "t0"', params: {} }
    generateSqlMock.mockResolvedValue(generated)
    renderPage()
    await selectConnectionAndSchema()
    await screen.findByText('employees')
    fireEvent.change(screen.getByTestId('from-join-tab-base-table-select'), { target: { value: 'employees' } })
    fireEvent.click(screen.getByTestId('generated-sql-panel-generate-button'))
    await screen.findByTestId('generated-sql-panel-sql')

    fireEvent.click(screen.getByText('実行'))

    expect(await screen.findByTestId('query-execution-page-stub')).toHaveTextContent(
      `connectionId=1, rawSql=${generated.sql}`,
    )
  })
})