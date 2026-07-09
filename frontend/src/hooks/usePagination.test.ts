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

import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { usePagination } from './usePagination'

describe('usePagination', () => {
  it('starts at page 0 with the given default page size and zero total count', () => {
    const { result } = renderHook(() => usePagination(20))

    expect(result.current.page).toBe(0)
    expect(result.current.pageSize).toBe(20)
    expect(result.current.totalCount).toBe(0)
    expect(result.current.pageRequest).toEqual({ page: 0, pageSize: 20 })
  })

  it('goToPage updates the current page', () => {
    const { result } = renderHook(() => usePagination(20))

    act(() => {
      result.current.goToPage(2)
    })

    expect(result.current.page).toBe(2)
    expect(result.current.pageRequest).toEqual({ page: 2, pageSize: 20 })
  })

  it('changePageSize updates the page size and resets the page to 0', () => {
    const { result } = renderHook(() => usePagination(20))
    act(() => {
      result.current.goToPage(3)
    })

    act(() => {
      result.current.changePageSize(50)
    })

    expect(result.current.pageSize).toBe(50)
    expect(result.current.page).toBe(0)
  })

  it('setTotalCount updates the reported total count', () => {
    const { result } = renderHook(() => usePagination(20))

    act(() => {
      result.current.setTotalCount(123)
    })

    expect(result.current.totalCount).toBe(123)
  })
})