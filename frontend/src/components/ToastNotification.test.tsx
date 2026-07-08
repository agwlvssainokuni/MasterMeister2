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