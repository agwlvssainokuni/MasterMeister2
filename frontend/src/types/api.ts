export interface PageRequest {
  page: number
  pageSize: number
}

export interface PageResult<T> {
  content: T[]
  totalCount: number
  page: number
  pageSize: number
}

export interface ErrorResponse {
  error: string
  message: string
}