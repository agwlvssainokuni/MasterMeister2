import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AuditLogFilterPanel } from './AuditLogFilterPanel'

describe('AuditLogFilterPanel', () => {
  it('calls onFilterChange with the entered filter values when searching', () => {
    const onFilterChange = vi.fn()
    render(<AuditLogFilterPanel filter={{}} onFilterChange={onFilterChange} />)

    fireEvent.change(screen.getByTestId('audit-log-filter-date-from-input'), {
      target: { value: '2026-01-01T00:00' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-date-to-input'), {
      target: { value: '2026-01-02T00:00' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-user-select'), { target: { value: '5' } })
    fireEvent.change(screen.getByTestId('audit-log-filter-category-select'), {
      target: { value: 'AUTHENTICATION' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-type-select'), {
      target: { value: 'LOGIN_SUCCESS' },
    })
    fireEvent.click(screen.getByTestId('audit-log-filter-search-button'))

    expect(onFilterChange).toHaveBeenCalledWith({
      dateFrom: '2026-01-01T00:00',
      dateTo: '2026-01-02T00:00',
      userId: 5,
      eventCategory: 'AUTHENTICATION',
      eventType: 'LOGIN_SUCCESS',
    })
  })

  it('resets the event type when the event category changes', () => {
    const onFilterChange = vi.fn()
    render(<AuditLogFilterPanel filter={{}} onFilterChange={onFilterChange} />)

    fireEvent.change(screen.getByTestId('audit-log-filter-category-select'), {
      target: { value: 'AUTHENTICATION' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-type-select'), {
      target: { value: 'LOGIN_SUCCESS' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-category-select'), {
      target: { value: 'DATA_ACCESS' },
    })
    fireEvent.click(screen.getByTestId('audit-log-filter-search-button'))

    expect(onFilterChange).toHaveBeenCalledWith(
      expect.objectContaining({ eventCategory: 'DATA_ACCESS', eventType: undefined }),
    )
  })

  it('shows a validation error and does not call onFilterChange when dateFrom is after dateTo', () => {
    const onFilterChange = vi.fn()
    render(<AuditLogFilterPanel filter={{}} onFilterChange={onFilterChange} />)

    fireEvent.change(screen.getByTestId('audit-log-filter-date-from-input'), {
      target: { value: '2026-01-02T00:00' },
    })
    fireEvent.change(screen.getByTestId('audit-log-filter-date-to-input'), {
      target: { value: '2026-01-01T00:00' },
    })
    fireEvent.click(screen.getByTestId('audit-log-filter-search-button'))

    expect(screen.getByTestId('audit-log-filter-error')).toBeInTheDocument()
    expect(onFilterChange).not.toHaveBeenCalled()
  })
})