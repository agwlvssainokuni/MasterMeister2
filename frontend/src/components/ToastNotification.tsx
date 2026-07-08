export type ToastSeverity = 'info' | 'success' | 'warning' | 'error'

interface ToastNotificationProps {
  message: string
  severity: ToastSeverity
}

export function ToastNotification({ message, severity }: ToastNotificationProps) {
  return (
    <div
      className={`toast-notification toast-notification-${severity}`}
      data-testid={`toast-notification-${severity}`}
      role="alert"
    >
      {message}
    </div>
  )
}