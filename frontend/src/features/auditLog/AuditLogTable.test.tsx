import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuditLogTable } from './AuditLogTable'
import type { AuditLog } from './types'

const rows: AuditLog[] = [
  {
    id: 1,
    occurredAt: '2026-01-01T00:00:00Z',
    userId: 1,
    connectionId: null,
    eventCategory: 'AUTHENTICATION',
    eventType: 'LOGIN_SUCCESS',
    result: 'SUCCESS',
    targetDescription: 'user@example.com',
    summaryMessage: null,
  },
]

describe('AuditLogTable', () => {
  it('shows a loading message while loading', () => {
    render(<AuditLogTable rows={[]} loading={true} />)

    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('renders a row for each audit log with a category badge', () => {
    render(<AuditLogTable rows={rows} loading={false} />)

    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('AUTHENTICATION')).toHaveClass('audit-log-badge-authentication')
  })

  it('renders a placeholder for null optional fields', () => {
    render(<AuditLogTable rows={rows} loading={false} />)

    expect(screen.getAllByText('-').length).toBeGreaterThan(0)
  })
})