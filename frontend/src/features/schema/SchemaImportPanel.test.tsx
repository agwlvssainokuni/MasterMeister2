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
import { importSchema } from './api'
import { SchemaImportPanel } from './SchemaImportPanel'

vi.mock('./api', () => ({
  importSchema: vi.fn(),
}))

const importSchemaMock = vi.mocked(importSchema)

describe('SchemaImportPanel', () => {
  beforeEach(() => {
    importSchemaMock.mockReset()
  })

  it('imports the schema for the given connection and shows the table count on success', async () => {
    importSchemaMock.mockResolvedValue({ success: true, tableCount: 5, message: 'ok' })
    render(<SchemaImportPanel connectionId={42} onClose={vi.fn()} />)

    fireEvent.click(screen.getByTestId('schema-import-panel-import-button'))

    expect(await screen.findByTestId('schema-import-panel-result-message')).toHaveTextContent(
      '取り込みに成功しました（5件）',
    )
    expect(importSchemaMock).toHaveBeenCalledWith(42)
  })

  it('shows a failure message when the import reports failure', async () => {
    importSchemaMock.mockResolvedValue({ success: false, tableCount: 0, message: 'schema not found' })
    render(<SchemaImportPanel connectionId={42} onClose={vi.fn()} />)

    fireEvent.click(screen.getByTestId('schema-import-panel-import-button'))

    expect(await screen.findByTestId('schema-import-panel-result-message')).toHaveTextContent(
      '取り込みに失敗しました: schema not found',
    )
  })

  it('shows a failure message when the import request throws', async () => {
    importSchemaMock.mockRejectedValue(new Error('network error'))
    render(<SchemaImportPanel connectionId={42} onClose={vi.fn()} />)

    fireEvent.click(screen.getByTestId('schema-import-panel-import-button'))

    expect(await screen.findByTestId('schema-import-panel-result-message')).toHaveTextContent('取り込みに失敗しました')
  })

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn()
    render(<SchemaImportPanel connectionId={42} onClose={onClose} />)

    fireEvent.click(screen.getByTestId('schema-import-panel-close-button'))

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
