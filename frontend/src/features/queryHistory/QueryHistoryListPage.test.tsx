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
import { useConnectionStore } from '../../store/connectionStore'
import { listHistory } from './api'
import { QueryHistoryListPage } from './QueryHistoryListPage'
import type { HistoryEntry } from './types'

vi.mock('./api', () => ({
  listHistory: vi.fn(),
}))

const listHistoryMock = vi.mocked(listHistory)

const savedEntry: HistoryEntry = {
  id: 1,
  userId: 1,
  connectionId: 1,
  schema: 'PUBLIC',
  sql: 'SELECT 1',
  params: {},
  resultCount: 1,
  elapsedMillis: 5,
  executedAt: '2026-01-01T00:00:00Z',
  savedQueryId: 3,
  savedQueryName: 'q1',
  executionCount: 2,
  retired: true,
  masked: false,
}

function DestinationStub() {
  const [searchParams] = useSearchParams()
  return (
    <div data-testid="destination-stub">
      rawSql={searchParams.get('rawSql')};schema={searchParams.get('schema')}
    </div>
  )
}

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/query-history" element={<QueryHistoryListPage />} />
        <Route path="/query-execution" element={<DestinationStub />} />
        <Route path="/saved-queries/new" element={<DestinationStub />} />
        <Route path="/query-builder" element={<DestinationStub />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('QueryHistoryListPage', () => {
  beforeEach(() => {
    listHistoryMock.mockReset()
    listHistoryMock.mockResolvedValue({ content: [savedEntry], totalCount: 1, page: 0, pageSize: 50 })
  })

  it('shows a message when the global connectionId is not set', () => {
    useConnectionStore.setState({ connectionId: null, connections: [] })
    renderPage('/query-history')

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
    expect(listHistoryMock).not.toHaveBeenCalled()
  })

  it('loads history for the global connectionId with default criteria on mount', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')

    expect(await screen.findByText('SELECT 1')).toBeInTheDocument()
    expect(listHistoryMock).toHaveBeenCalledWith(
      1,
      { executorScope: 'ALL' },
      { page: 0, pageSize: 50 },
    )
  })

  it('shows the schema column, saved-query name, and the "廃止済み" badge for a saved-query row', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')

    expect(await screen.findByText('保存クエリ: q1')).toBeInTheDocument()
    expect(screen.getByText('PUBLIC')).toBeInTheDocument()
    expect(screen.getByText('廃止済み')).toBeInTheDocument()
  })

  it('re-runs the search with updated filters when the search button is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')
    await screen.findByText('SELECT 1')

    fireEvent.change(screen.getByTestId('query-history-list-page-sql-text-search-input'), {
      target: { value: 'employees' },
    })
    fireEvent.click(screen.getByTestId('query-history-list-page-search-button'))

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(listHistoryMock).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ sqlTextSearch: 'employees' }),
      { page: 0, pageSize: 50 },
    )
  })

  it('re-runs the search when the executor scope is changed to SELF', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')
    await screen.findByText('SELECT 1')

    fireEvent.change(screen.getByTestId('query-history-list-page-executor-scope-select'), {
      target: { value: 'SELF' },
    })

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(listHistoryMock).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ executorScope: 'SELF' }),
      { page: 0, pageSize: 50 },
    )
  })

  it('navigates to the execution page with rawSql/schema (no connectionId) when "再実行" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')
    await screen.findByText('SELECT 1')

    fireEvent.click(screen.getByTestId('query-history-list-page-rerun-button'))

    expect(await screen.findByTestId('destination-stub')).toHaveTextContent('rawSql=SELECT 1;schema=PUBLIC')
  })

  it('navigates to the save form with rawSql only (no schema/connectionId) when "保存" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')
    await screen.findByText('SELECT 1')

    fireEvent.click(screen.getByTestId('query-history-list-page-save-button'))

    expect(await screen.findByTestId('destination-stub')).toHaveTextContent('rawSql=SELECT 1;schema=')
  })

  it('navigates to the query builder with rawSql/schema (no connectionId) when "ビルダーで編集" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage('/query-history')
    await screen.findByText('SELECT 1')

    fireEvent.click(screen.getByTestId('query-history-list-page-edit-in-builder-button'))

    expect(await screen.findByTestId('destination-stub')).toHaveTextContent('rawSql=SELECT 1;schema=PUBLIC')
  })
})