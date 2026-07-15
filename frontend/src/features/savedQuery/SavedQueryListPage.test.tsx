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
import { listQueries } from './api'
import { SavedQueryListPage } from './SavedQueryListPage'
import type { SavedQuerySummary } from './types'

vi.mock('./api', () => ({
  listQueries: vi.fn(),
}))

const listQueriesMock = vi.mocked(listQueries)

const summary: SavedQuerySummary = { id: 10, name: 'q1', visibility: 'PUBLIC', retired: false, ownerId: 1 }

function ExecutionPageStub() {
  const [searchParams] = useSearchParams()
  return (
    <div data-testid="query-execution-page-stub">
      connectionId={searchParams.get('connectionId')};savedQueryId={searchParams.get('savedQueryId')}
    </div>
  )
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/saved-queries']}>
      <Routes>
        <Route path="/saved-queries" element={<SavedQueryListPage />} />
        <Route path="/query-execution" element={<ExecutionPageStub />} />
        <Route path="/saved-queries/:id" element={<div data-testid="saved-query-detail-page-stub" />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SavedQueryListPage', () => {
  beforeEach(() => {
    listQueriesMock.mockReset()
    listQueriesMock.mockResolvedValue([summary])
  })

  it('shows a message when the global connectionId is not set', () => {
    useConnectionStore.setState({ connectionId: null, connections: [] })
    renderPage()

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
    expect(listQueriesMock).not.toHaveBeenCalled()
  })

  it('lists saved queries for the global connectionId with includeRetired=false by default', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()

    expect(await screen.findByText('q1')).toBeInTheDocument()
    expect(listQueriesMock).toHaveBeenCalledWith(1, false)
  })

  it('reloads with includeRetired=true when the toggle is checked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await screen.findByText('q1')

    fireEvent.click(screen.getByTestId('saved-query-list-page-include-retired-checkbox'))

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(listQueriesMock).toHaveBeenLastCalledWith(1, true)
  })

  it('navigates to the execution page with only savedQueryId (no connectionId) when "実行" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await screen.findByText('q1')

    fireEvent.click(screen.getByTestId('saved-query-list-page-execute-button'))

    expect(await screen.findByTestId('query-execution-page-stub')).toHaveTextContent(
      'connectionId=;savedQueryId=10',
    )
  })

  it('navigates to the detail page when "詳細" is clicked', async () => {
    useConnectionStore.setState({ connectionId: 1, connections: [] })
    renderPage()
    await screen.findByText('q1')

    fireEvent.click(screen.getByTestId('saved-query-list-page-detail-button'))

    expect(await screen.findByTestId('saved-query-detail-page-stub')).toBeInTheDocument()
  })
})