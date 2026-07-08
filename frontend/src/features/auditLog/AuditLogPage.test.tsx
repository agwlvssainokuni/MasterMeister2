import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchAuditLogs } from './api'
import { AuditLogPage } from './AuditLogPage'
import type { AuditLog } from './types'

vi.mock('./api', () => ({
  searchAuditLogs: vi.fn(),
}))

const searchAuditLogsMock = vi.mocked(searchAuditLogs)

const row: AuditLog = {
  id: 1,
  occurredAt: '2026-01-01T00:00:00Z',
  userId: 1,
  connectionId: null,
  eventCategory: 'AUTHENTICATION',
  eventType: 'LOGIN_SUCCESS',
  result: 'SUCCESS',
  targetDescription: 'user@example.com',
  summaryMessage: null,
}

describe('AuditLogPage', () => {
  beforeEach(() => {
    searchAuditLogsMock.mockReset()
    searchAuditLogsMock.mockResolvedValue({ content: [row], totalCount: 1, page: 0, pageSize: 20 })
  })

  it('loads and displays audit logs on initial render', async () => {
    render(<AuditLogPage />)

    await waitFor(() => expect(searchAuditLogsMock).toHaveBeenCalledWith({}, { page: 0, pageSize: 20 }))
    expect(await screen.findByText('user@example.com')).toBeInTheDocument()
  })

  it('re-runs the search with updated filters when the filter panel submits', async () => {
    render(<AuditLogPage />)
    await screen.findByText('user@example.com')

    fireEvent.change(screen.getByTestId('audit-log-filter-category-select'), {
      target: { value: 'AUTHENTICATION' },
    })
    fireEvent.click(screen.getByTestId('audit-log-filter-search-button'))

    await waitFor(() =>
      expect(searchAuditLogsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ eventCategory: 'AUTHENTICATION' }),
        { page: 0, pageSize: 20 },
      ),
    )
  })
})