import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { requestRegistration } from './api/userRegistrationApi'
import { RegistrationRequestPage } from './RegistrationRequestPage'

vi.mock('./api/userRegistrationApi', () => ({
  requestRegistration: vi.fn(),
}))

const requestRegistrationMock = vi.mocked(requestRegistration)

describe('RegistrationRequestPage', () => {
  beforeEach(() => {
    requestRegistrationMock.mockReset()
  })

  it('shows the same success message regardless of whether the email is registered', async () => {
    requestRegistrationMock.mockResolvedValue(undefined)
    render(<RegistrationRequestPage />)

    fireEvent.change(screen.getByTestId('registration-request-page-email-input'), {
      target: { value: 'user@example.com' },
    })
    fireEvent.click(screen.getByTestId('registration-request-page-submit-button'))

    expect(await screen.findByTestId('registration-request-page-success-message')).toBeInTheDocument()
  })

  it('shows the same success message even when the request fails', async () => {
    requestRegistrationMock.mockRejectedValue(new Error('network error'))
    render(<RegistrationRequestPage />)

    fireEvent.change(screen.getByTestId('registration-request-page-email-input'), {
      target: { value: 'user@example.com' },
    })
    fireEvent.click(screen.getByTestId('registration-request-page-submit-button'))

    expect(await screen.findByTestId('registration-request-page-success-message')).toBeInTheDocument()
  })
})