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