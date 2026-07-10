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

import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { requestRegistration } from './api'
import { RegistrationRequestPage } from './RegistrationRequestPage'

vi.mock('./api', () => ({
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