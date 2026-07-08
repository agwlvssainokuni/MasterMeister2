import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renders the message', () => {
    render(<ConfirmDialog message="Delete this record?" onConfirm={vi.fn()} onCancel={vi.fn()} />)

    expect(screen.getByText('Delete this record?')).toBeInTheDocument()
  })

  it('calls onConfirm when the confirm button is clicked', () => {
    const onConfirm = vi.fn()
    render(<ConfirmDialog message="Delete this record?" onConfirm={onConfirm} onCancel={vi.fn()} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-confirm-button'))

    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the cancel button is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmDialog message="Delete this record?" onConfirm={vi.fn()} onCancel={onCancel} />)

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel-button'))

    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})