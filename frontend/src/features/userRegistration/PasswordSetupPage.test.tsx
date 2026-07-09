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
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/apiClient'
import { completeRegistration } from './api/userRegistrationApi'
import { PasswordSetupPage } from './PasswordSetupPage'

vi.mock('./api/userRegistrationApi', () => ({
  completeRegistration: vi.fn(),
}))

const completeRegistrationMock = vi.mocked(completeRegistration)

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/register/complete" element={<PasswordSetupPage />} />
        <Route path="/register" element={<div>Registration Request Page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PasswordSetupPage', () => {
  beforeEach(() => {
    completeRegistrationMock.mockReset()
  })

  it('shows an immediate error and a re-request link when the URL has no token', () => {
    renderAt('/register/complete')

    expect(screen.getByTestId('password-setup-page-error-message')).toBeInTheDocument()
    expect(screen.queryByTestId('password-setup-page-password-input')).not.toBeInTheDocument()
  })

  it('shows a client-side error when the passwords do not match', async () => {
    renderAt('/register/complete?token=valid-token')

    fireEvent.change(screen.getByTestId('password-setup-page-password-input'), { target: { value: 'password1' } })
    fireEvent.change(screen.getByTestId('password-setup-page-confirm-password-input'), {
      target: { value: 'password2' },
    })
    fireEvent.click(screen.getByTestId('password-setup-page-submit-button'))

    expect(await screen.findByTestId('password-setup-page-error-message')).toHaveTextContent('パスワードが一致しません')
    expect(completeRegistrationMock).not.toHaveBeenCalled()
  })

  it('shows the success message when completion succeeds', async () => {
    completeRegistrationMock.mockResolvedValue(undefined)
    renderAt('/register/complete?token=valid-token')

    fireEvent.change(screen.getByTestId('password-setup-page-password-input'), { target: { value: 'password1' } })
    fireEvent.change(screen.getByTestId('password-setup-page-confirm-password-input'), {
      target: { value: 'password1' },
    })
    fireEvent.click(screen.getByTestId('password-setup-page-submit-button'))

    expect(await screen.findByTestId('password-setup-page-success-message')).toBeInTheDocument()
    expect(completeRegistrationMock).toHaveBeenCalledWith('valid-token', 'password1')
  })

  it('shows the invalid-token error and a re-request link when the token has expired', async () => {
    completeRegistrationMock.mockRejectedValue(new ApiError(400, 'TOKEN_EXPIRED', 'Token has expired'))
    renderAt('/register/complete?token=expired-token')

    fireEvent.change(screen.getByTestId('password-setup-page-password-input'), { target: { value: 'password1' } })
    fireEvent.change(screen.getByTestId('password-setup-page-confirm-password-input'), {
      target: { value: 'password1' },
    })
    fireEvent.click(screen.getByTestId('password-setup-page-submit-button'))

    expect(await screen.findByTestId('password-setup-page-error-message')).toBeInTheDocument()
    expect(screen.getByText('再申請はこちら')).toBeInTheDocument()
  })
})