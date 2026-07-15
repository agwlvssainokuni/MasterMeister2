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
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/apiClient'
import { useConnectionStore } from '../../store/connectionStore'
import { getQuery } from '../savedQuery/api'
import type { SavedQueryDetail } from '../savedQuery/types'
import { executeAdhocSql, executeSavedQuery, listAccessibleSchemas } from './api'
import { QueryExecutionPage } from './QueryExecutionPage'
import type { QueryResult } from './types'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, executeAdhocSql: vi.fn(), executeSavedQuery: vi.fn(), listAccessibleSchemas: vi.fn() }
})
vi.mock('../savedQuery/api', () => ({
  getQuery: vi.fn(),
}))

const executeAdhocSqlMock = vi.mocked(executeAdhocSql)
const executeSavedQueryMock = vi.mocked(executeSavedQuery)
const listAccessibleSchemasMock = vi.mocked(listAccessibleSchemas)
const getQueryMock = vi.mocked(getQuery)

const result: QueryResult = {
  columns: [{ columnName: 'id', dataType: 'INTEGER' }],
  rows: [[1]],
  totalRows: 1,
  truncated: false,
}

const savedQueryDetail: SavedQueryDetail = {
  id: 7,
  ownerId: 1,
  connectionId: 1,
  name: 'q1',
  sql: 'SELECT * FROM t WHERE id = :id',
  visibility: 'PUBLIC',
  retired: false,
  executionCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <QueryExecutionPage />
    </MemoryRouter>,
  )
}

describe('QueryExecutionPage', () => {
  beforeEach(() => {
    executeAdhocSqlMock.mockReset()
    executeSavedQueryMock.mockReset()
    listAccessibleSchemasMock.mockReset()
    listAccessibleSchemasMock.mockResolvedValue(['S1', 'S2'])
    getQueryMock.mockReset()
  })

  it('shows a message when the global connectionId is not set (adhoc mode)', () => {
    useConnectionStore.setState({ connectionId: null, connections: [] })
    renderPage('/query-execution')

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
  })

  it('detects :param placeholders in the (editable) rawSql and shows an input for each', () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-execution?rawSql=SELECT%20*%20FROM%20t%20WHERE%20id%20%3D%20%3Aid')

    expect(screen.getByTestId('query-execution-page-sql-textarea')).not.toHaveAttribute('readonly')
    expect(screen.getByTestId('query-execution-page-param-id')).toBeInTheDocument()
  })

  it('disables the execute button until a schema is selected', () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-execution?rawSql=SELECT%201')

    expect(screen.getByTestId('query-execution-page-execute-button')).toBeDisabled()
  })

  it('prefills the schema select from the schema URL query parameter', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-execution?rawSql=SELECT%201&schema=S2')

    expect(await screen.findByTestId('query-execution-page-schema-select')).toHaveValue('S2')
  })

  it('executes adhoc SQL with the selected schema/connectionId and entered parameter values', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    executeAdhocSqlMock.mockResolvedValue(result)
    renderPage('/query-execution?rawSql=SELECT%20*%20FROM%20t%20WHERE%20id%20%3D%20%3Aid')

    await screen.findByRole('option', { name: 'S1' })
    fireEvent.change(screen.getByTestId('query-execution-page-schema-select'), { target: { value: 'S1' } })
    fireEvent.change(screen.getByTestId('query-execution-page-param-id'), { target: { value: '42' } })
    fireEvent.click(screen.getByTestId('query-execution-page-execute-button'))

    expect(await screen.findByTestId('query-execution-page-result')).toBeInTheDocument()
    expect(executeAdhocSqlMock).toHaveBeenCalledWith(
      1,
      'S1',
      'SELECT * FROM t WHERE id = :id',
      { id: '42' },
      { enabled: false, page: 0, pageSize: 50 },
    )
  })

  it(
    'loads the saved query SQL read-only and uses the saved query connectionId ' +
      '(not the global one) when executing',
    async () => {
      useConnectionStore.setState({ connectionId: 99, connections: [] })
      getQueryMock.mockResolvedValue(savedQueryDetail)
      executeSavedQueryMock.mockResolvedValue(result)
      renderPage('/query-execution?savedQueryId=7')

      expect(await screen.findByTestId('query-execution-page-sql-textarea')).toHaveValue(savedQueryDetail.sql)
      expect(screen.getByTestId('query-execution-page-sql-textarea')).toHaveAttribute('readonly')
      expect(listAccessibleSchemasMock).toHaveBeenCalledWith(savedQueryDetail.connectionId)

      await screen.findByRole('option', { name: 'S1' })
      fireEvent.change(screen.getByTestId('query-execution-page-schema-select'), { target: { value: 'S1' } })
      fireEvent.click(screen.getByTestId('query-execution-page-execute-button'))

      expect(await screen.findByTestId('query-execution-page-result')).toBeInTheDocument()
      expect(executeSavedQueryMock).toHaveBeenCalledWith(
        savedQueryDetail.connectionId,
        'S1',
        7,
        { id: '' },
        { enabled: false, page: 0, pageSize: 50 },
      )
    },
  )

  it('shows an error message when execution fails validation', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    executeAdhocSqlMock.mockRejectedValue(
      new ApiError(400, 'VALIDATION_ERROR', '読み取り専用SQL（SELECT文）1件のみ実行できます'),
    )
    renderPage('/query-execution?rawSql=DELETE%20FROM%20t&schema=S1')

    await screen.findByTestId('query-execution-page-schema-select')
    fireEvent.click(screen.getByTestId('query-execution-page-execute-button'))

    expect(await screen.findByTestId('query-execution-page-error')).toHaveTextContent(
      '読み取り専用SQL（SELECT文）1件のみ実行できます',
    )
  })

  it('re-executes with the next page when paging is enabled and the next button is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    executeAdhocSqlMock.mockResolvedValue(result)
    renderPage('/query-execution?rawSql=SELECT%201&schema=S1')

    await screen.findByTestId('query-execution-page-schema-select')
    fireEvent.click(screen.getByTestId('query-execution-page-paging-checkbox'))
    fireEvent.click(screen.getByTestId('query-execution-page-execute-button'))
    await screen.findByTestId('query-execution-page-result')

    fireEvent.click(screen.getByTestId('query-execution-page-next-button'))

    await screen.findByText('2ページ')
    expect(executeAdhocSqlMock).toHaveBeenLastCalledWith(
      1,
      'S1',
      'SELECT 1',
      {},
      { enabled: true, page: 1, pageSize: 50 },
    )
  })
})