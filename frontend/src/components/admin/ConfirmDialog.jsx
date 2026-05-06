function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  busy = false,
  onConfirm,
  onCancel,
}) {
  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[1100] flex items-center justify-center p-4 bg-inverse-surface/40 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
    >
      <div className="w-full max-w-md rounded-2xl bg-surface-container-lowest shadow-xl p-6">
        <h2 id="confirm-dialog-title" className="text-lg font-semibold font-headline text-on-surface mb-2">
          {title}
        </h2>
        {message && <p className="text-on-surface-variant mb-6">{message}</p>}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-4 py-2 rounded-lg text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className={[
              'px-4 py-2 rounded-lg font-semibold text-white transition-colors disabled:opacity-50',
              destructive ? 'bg-error hover:bg-error-dim' : 'bg-primary hover:bg-primary-dim',
            ].join(' ')}
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmDialog
