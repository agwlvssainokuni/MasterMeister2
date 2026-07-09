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

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ToastNotification } from './ToastNotification'
import type { ToastSeverity } from './ToastNotification'

describe('ToastNotification', () => {
  it.each<ToastSeverity>(['info', 'success', 'warning', 'error'])(
    'renders the message with a severity-specific data-testid for %s',
    (severity) => {
      render(<ToastNotification message="Something happened" severity={severity} />)

      const toast = screen.getByTestId(`toast-notification-${severity}`)
      expect(toast).toHaveTextContent('Something happened')
    },
  )
})