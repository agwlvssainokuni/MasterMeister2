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
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('disables the prev button on the first page and the next button on the last page', () => {
    render(
      <Pagination
        page={0}
        pageSize={20}
        pageSizeOptions={[20, 50, 100]}
        totalCount={15}
        onPageChange={vi.fn()}
        onPageSizeChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('pagination-prev-button')).toBeDisabled()
    expect(screen.getByTestId('pagination-next-button')).toBeDisabled()
  })

  it('calls onPageChange with the next/previous page index', () => {
    const onPageChange = vi.fn()
    render(
      <Pagination
        page={1}
        pageSize={20}
        pageSizeOptions={[20, 50, 100]}
        totalCount={100}
        onPageChange={onPageChange}
        onPageSizeChange={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByTestId('pagination-next-button'))
    expect(onPageChange).toHaveBeenCalledWith(2)

    fireEvent.click(screen.getByTestId('pagination-prev-button'))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('calls onPageSizeChange when a new page size is selected', () => {
    const onPageSizeChange = vi.fn()
    render(
      <Pagination
        page={0}
        pageSize={20}
        pageSizeOptions={[20, 50, 100]}
        totalCount={100}
        onPageChange={vi.fn()}
        onPageSizeChange={onPageSizeChange}
      />,
    )

    fireEvent.change(screen.getByTestId('pagination-page-size-select'), { target: { value: '50' } })

    expect(onPageSizeChange).toHaveBeenCalledWith(50)
  })
})