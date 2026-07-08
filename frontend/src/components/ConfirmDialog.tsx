interface ConfirmDialogProps {
  message: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ message, onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <div className="confirm-dialog" role="dialog">
      <p>{message}</p>
      <button type="button" data-testid="confirm-dialog-confirm-button" onClick={onConfirm}>
        OK
      </button>
      <button type="button" data-testid="confirm-dialog-cancel-button" onClick={onCancel}>
        キャンセル
      </button>
    </div>
  )
}