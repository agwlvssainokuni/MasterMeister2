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
import { MutationResultDialog } from './MutationResultDialog'
import type { MutationResult } from './types'

describe('MutationResultDialog', () => {
  it('renders nothing when result is null', () => {
    const { container } = render(<MutationResultDialog result={null} onClose={vi.fn()} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('shows the created/updated/deleted counts on success', () => {
    const result: MutationResult = { success: true, createdCount: 1, updatedCount: 2, deletedCount: 3, errorMessage: null }
    render(<MutationResultDialog result={result} onClose={vi.fn()} />)

    expect(screen.getByText('反映が完了しました')).toBeInTheDocument()
    expect(screen.getByText('作成: 1件 / 更新: 2件 / 削除: 3件')).toBeInTheDocument()
  })

  it('shows the error message on failure', () => {
    const result: MutationResult = {
      success: false,
      createdCount: 0,
      updatedCount: 0,
      deletedCount: 0,
      errorMessage: '一意制約違反です',
    }
    render(<MutationResultDialog result={result} onClose={vi.fn()} />)

    expect(screen.getByText('反映に失敗しました')).toBeInTheDocument()
    expect(screen.getByText('一意制約違反です')).toBeInTheDocument()
  })

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn()
    const result: MutationResult = { success: true, createdCount: 0, updatedCount: 0, deletedCount: 0, errorMessage: null }
    render(<MutationResultDialog result={result} onClose={onClose} />)

    fireEvent.click(screen.getByTestId('mutation-result-dialog-close'))

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})