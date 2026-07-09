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

import { useCallback, useMemo, useState } from 'react'
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

  const pageRequest: PageRequest = useMemo(() => ({ page, pageSize }), [page, pageSize])

  return { page, pageSize, totalCount, pageRequest, setTotalCount, goToPage, changePageSize }
}