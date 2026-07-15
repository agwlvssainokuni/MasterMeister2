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
import { useAuthStore } from '../../store/authStore'
import { getQuery, retireQuery, updateQuery } from './api'
import { SavedQueryDetailPage } from './SavedQueryDetailPage'
import type { SavedQueryDetail } from './types'

vi.mock('./api', () => ({
  getQuery: vi.fn(),
  updateQuery: vi.fn(),
  retireQuery: vi.fn(),
}))

const getQueryMock = vi.mocked(getQuery)
const updateQueryMock = vi.mocked(updateQuery)
const retireQueryMock = vi.mocked(retireQuery)

const detail: SavedQueryDetail = {
  id: 5,
  ownerId: 1,
  connectionId: 2,
  name: 'q1',
  sql: 'SELECT 1',
  visibility: 'PRIVATE',
  retired: false,
  executionCount: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

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
    <MemoryRouter initialEntries={['/saved-queries/5']}>
      <Routes>
        <Route path="/saved-queries/:id" element={<SavedQueryDetailPage />} />
        <Route path="/query-execution" element={<ExecutionPageStub />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SavedQueryDetailPage', () => {
  beforeEach(() => {
    getQueryMock.mockReset()
    updateQueryMock.mockReset()
    retireQueryMock.mockReset()
    getQueryMock.mockResolvedValue(detail)
    useAuthStore.setState({ currentUser: null, token: null, refreshToken: null })
  })

  it('loads and displays the saved query detail', async () => {
    renderPage()

    expect(await screen.findByTestId('saved-query-detail-page-sql')).toHaveTextContent('SELECT 1')
  })

  it('disables edit/retire buttons for a non-owner viewer', async () => {
    useAuthStore.setState({ currentUser: { id: 99, email: 'other@example.com', role: 'USER' }, token: 't', refreshToken: 'r' })
    renderPage()
    await screen.findByTestId('saved-query-detail-page-sql')

    expect(screen.getByTestId('saved-query-detail-page-edit-button')).toBeDisabled()
    expect(screen.getByTestId('saved-query-detail-page-retire-button')).toBeDisabled()
  })

  it('enables edit/retire buttons for the owner and saves changes via updateQuery', async () => {
    useAuthStore.setState({ currentUser: { id: 1, email: 'owner@example.com', role: 'USER' }, token: 't', refreshToken: 'r' })
    updateQueryMock.mockResolvedValue(undefined)
    getQueryMock.mockResolvedValueOnce(detail).mockResolvedValue({ ...detail, name: 'renamed' })
    renderPage()
    await screen.findByTestId('saved-query-detail-page-sql')

    fireEvent.click(screen.getByTestId('saved-query-detail-page-edit-button'))
    fireEvent.change(screen.getByTestId('saved-query-detail-page-name-input'), { target: { value: 'renamed' } })
    fireEvent.click(screen.getByTestId('saved-query-detail-page-save-button'))

    await screen.findByText('renamed')
    expect(updateQueryMock).toHaveBeenCalledWith(5, 'renamed', 'SELECT 1', 'PRIVATE')
  })

  it('retires the query after confirming the dialog', async () => {
    useAuthStore.setState({ currentUser: { id: 1, email: 'owner@example.com', role: 'USER' }, token: 't', refreshToken: 'r' })
    retireQueryMock.mockResolvedValue(undefined)
    getQueryMock.mockResolvedValueOnce(detail).mockResolvedValue({ ...detail, retired: true })
    renderPage()
    await screen.findByTestId('saved-query-detail-page-sql')

    fireEvent.click(screen.getByTestId('saved-query-detail-page-retire-button'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(await screen.findByTestId('saved-query-detail-page-retired-badge')).toBeInTheDocument()
    expect(retireQueryMock).toHaveBeenCalledWith(5)
  })

  it('navigates to the execution page with only savedQueryId (no connectionId) when "実行" is clicked', async () => {
    renderPage()
    await screen.findByTestId('saved-query-detail-page-sql')

    fireEvent.click(screen.getByTestId('saved-query-detail-page-execute-button'))

    expect(await screen.findByTestId('query-execution-page-stub')).toHaveTextContent(
      'connectionId=;savedQueryId=5',
    )
  })
})