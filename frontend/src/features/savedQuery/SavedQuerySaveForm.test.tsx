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
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/apiClient'
import { saveQuery } from './api'
import { SavedQuerySaveForm } from './SavedQuerySaveForm'

vi.mock('./api', () => ({
  saveQuery: vi.fn(),
}))

const saveQueryMock = vi.mocked(saveQuery)

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/saved-queries/new" element={<SavedQuerySaveForm />} />
        <Route path="/saved-queries/:id" element={<div data-testid="saved-query-detail-page-stub" />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SavedQuerySaveForm', () => {
  beforeEach(() => {
    saveQueryMock.mockReset()
  })

  it('shows a message when connectionId is missing from the URL', () => {
    renderPage('/saved-queries/new')

    expect(screen.getByText('接続が指定されていません。')).toBeInTheDocument()
  })

  it('prefills the SQL textarea from the rawSql query parameter', () => {
    renderPage('/saved-queries/new?connectionId=1&rawSql=SELECT%201')

    expect(screen.getByTestId('saved-query-save-form-sql-textarea')).toHaveValue('SELECT 1')
  })

  it('saves the query and navigates to its detail page on success', async () => {
    saveQueryMock.mockResolvedValue(42)
    renderPage('/saved-queries/new?connectionId=1&rawSql=SELECT%201')

    fireEvent.change(screen.getByTestId('saved-query-save-form-name-input'), { target: { value: 'my-query' } })
    fireEvent.change(screen.getByTestId('saved-query-save-form-visibility-select'), { target: { value: 'PUBLIC' } })
    fireEvent.click(screen.getByTestId('saved-query-save-form-submit-button'))

    expect(await screen.findByTestId('saved-query-detail-page-stub')).toBeInTheDocument()
    expect(saveQueryMock).toHaveBeenCalledWith(1, 'my-query', 'SELECT 1', 'PUBLIC')
  })

  it('shows an error message when saving fails', async () => {
    saveQueryMock.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', '名前は必須です'))
    renderPage('/saved-queries/new?connectionId=1')

    fireEvent.click(screen.getByTestId('saved-query-save-form-submit-button'))

    expect(await screen.findByTestId('saved-query-save-form-error')).toHaveTextContent('名前は必須です')
  })
})