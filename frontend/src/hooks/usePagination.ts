import { useCallback, useState } from 'react'
import type { PageRequest } from '../types/api'

export function usePagination(defaultPageSize: number) {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(defaultPageSize)
  const [totalCount, setTotalCount] = useState(0)

  const goToPage = useCallback((nextPage: number) => {
    setPage(nextPage)
  }, [])

  const changePageSize = useCallback((nextPageSize: number) => {
    setPageSize(nextPageSize)
    setPage(0)
  }, [])

  const pageRequest: PageRequest = { page, pageSize }

  return { page, pageSize, totalCount, pageRequest, setTotalCount, goToPage, changePageSize }
}