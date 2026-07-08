interface PaginationProps {
  page: number
  pageSize: number
  pageSizeOptions: number[]
  totalCount: number
  onPageChange: (page: number) => void
  onPageSizeChange: (pageSize: number) => void
}

export function Pagination({
  page,
  pageSize,
  pageSizeOptions,
  totalCount,
  onPageChange,
  onPageSizeChange,
}: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))

  return (
    <div className="pagination">
      <button
        type="button"
        data-testid="pagination-prev-button"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        前へ
      </button>
      <span>
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        data-testid="pagination-next-button"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        次へ
      </button>
      <select
        data-testid="pagination-page-size-select"
        value={pageSize}
        onChange={(event) => onPageSizeChange(Number(event.target.value))}
      >
        {pageSizeOptions.map((size) => (
          <option key={size} value={size}>
            {size}
          </option>
        ))}
      </select>
    </div>
  )
}