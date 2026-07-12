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
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { parseSql } from './api'
import { SqlReverseParsePanel } from './SqlReverseParsePanel'
import type { ParseResult, QueryBuilderModel } from './types'

vi.mock('./api', () => ({
  parseSql: vi.fn(),
}))

const parseSqlMock = vi.mocked(parseSql)

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

describe('SqlReverseParsePanel', () => {
  beforeEach(() => {
    parseSqlMock.mockReset()
  })

  it('calls parseSql and onApply when the parsed result is fully parsed', async () => {
    const result: ParseResult = { fullyParsed: true, model, notice: null }
    parseSqlMock.mockResolvedValue(result)
    const onApply = vi.fn()
    render(<SqlReverseParsePanel connectionId={1} onApply={onApply} />)

    fireEvent.change(screen.getByTestId('sql-reverse-parse-panel-raw-sql-input'), {
      target: { value: 'SELECT t0.id FROM public.employees t0' },
    })
    fireEvent.click(screen.getByTestId('sql-reverse-parse-panel-parse-button'))

    expect(parseSqlMock).toHaveBeenCalledWith(1, 'SELECT t0.id FROM public.employees t0')
    await screen.findByTestId('sql-reverse-parse-panel-raw-sql-input')
    expect(onApply).toHaveBeenCalledWith(model)
  })

  it('shows the notice and does not call onApply when the SQL is not fully parsed', async () => {
    const result: ParseResult = { fullyParsed: false, model: null, notice: '対応していない構文です' }
    parseSqlMock.mockResolvedValue(result)
    const onApply = vi.fn()
    render(<SqlReverseParsePanel connectionId={1} onApply={onApply} />)

    fireEvent.click(screen.getByTestId('sql-reverse-parse-panel-parse-button'))

    expect(await screen.findByTestId('sql-reverse-parse-panel-notice')).toHaveTextContent('対応していない構文です')
    expect(onApply).not.toHaveBeenCalled()
  })

  it('auto-parses on mount when initialRawSql is provided', async () => {
    const result: ParseResult = { fullyParsed: true, model, notice: null }
    parseSqlMock.mockResolvedValue(result)
    const onApply = vi.fn()
    render(
      <SqlReverseParsePanel connectionId={1} initialRawSql="SELECT t0.id FROM public.employees t0" onApply={onApply} />,
    )

    await screen.findByDisplayValue('SELECT t0.id FROM public.employees t0')
    expect(parseSqlMock).toHaveBeenCalledWith(1, 'SELECT t0.id FROM public.employees t0')
    expect(onApply).toHaveBeenCalledWith(model)
  })
})