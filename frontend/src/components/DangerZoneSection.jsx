import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDeleteAccount } from '../hooks/useProfile.js'

/**
 * "Danger zone" section for ProfilePage. Closes #602 (1.1.1.2.12).
 *
 * One destructive button → confirm modal that requires typing the user's own
 * email to enable the actual delete action. On success the auth state is
 * cleared (handled by useDeleteAccount) and the user is redirected to `/`.
 * Server-side anonymization preserves the user's reports/comments/votes.
 */
function DangerZoneSection({ email }) {
  const [showConfirm, setShowConfirm] = useState(false)

  return (
    <section className="bg-error/5 border border-error/30 rounded-2xl p-6 flex flex-col gap-3">
      <h2 className="text-xl font-bold font-headline text-error">Danger zone</h2>
      <p className="text-sm text-on-surface-variant">
        Permanently delete your account. Your reports, comments, and validation
        votes stay on the map but are anonymized — they'll show as submitted by
        "Deleted User". This cannot be undone.
      </p>
      <div>
        <button
          type="button"
          onClick={() => setShowConfirm(true)}
          className="px-4 py-2 rounded-lg bg-error text-on-error font-semibold cursor-pointer hover:bg-error/90 transition-colors"
        >
          Delete my account
        </button>
      </div>

      {showConfirm && (
        <DeleteAccountConfirmModal
          email={email}
          onClose={() => setShowConfirm(false)}
        />
      )}
    </section>
  )
}

function DeleteAccountConfirmModal({ email, onClose }) {
  const navigate = useNavigate()
  const deleteAccountMutation = useDeleteAccount()
  const [typedEmail, setTypedEmail] = useState('')
  const [serverError, setServerError] = useState('')
  const inputRef = useRef(null)

  useEffect(() => {
    inputRef.current?.focus()
    function onKey(e) {
      if (e.key === 'Escape' && !deleteAccountMutation.isPending) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose, deleteAccountMutation.isPending])

  const matches = typedEmail.trim().toLowerCase() === (email ?? '').toLowerCase()
  const disabled = !matches || deleteAccountMutation.isPending

  async function handleConfirm() {
    setServerError('')
    try {
      await deleteAccountMutation.mutateAsync()
      // Modal will unmount with the page; navigate as the page renders the
      // logged-out shell anyway, so this is mostly a UX nicety.
      navigate('/')
    } catch (e) {
      setServerError(e.message || 'Failed to delete account.')
    }
  }

  function handleBackdrop(e) {
    if (e.target === e.currentTarget && !deleteAccountMutation.isPending) onClose()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Confirm account deletion"
      onClick={handleBackdrop}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-xl w-full max-w-md flex flex-col p-6 gap-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold font-headline text-on-surface">Delete account</h3>
          <button
            type="button"
            onClick={onClose}
            disabled={deleteAccountMutation.isPending}
            aria-label="Close"
            className="w-9 h-9 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors disabled:opacity-50 cursor-pointer"
          >
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <p className="text-sm text-on-surface-variant">
          This permanently deletes your account. To confirm, type your email
          (<span className="font-semibold text-on-surface">{email}</span>) below.
        </p>

        <label htmlFor="confirm-email" className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">
          Confirm email
        </label>
        <input
          ref={inputRef}
          id="confirm-email"
          type="email"
          autoComplete="off"
          value={typedEmail}
          onChange={(e) => setTypedEmail(e.target.value)}
          disabled={deleteAccountMutation.isPending}
          className="w-full px-4 py-2.5 rounded-xl bg-surface-container text-on-surface border border-outline-variant/30 focus:outline-none focus:ring-2 focus:ring-error/40 disabled:opacity-50"
        />

        {serverError && (
          <p role="alert" className="text-sm text-error">
            {serverError}
          </p>
        )}

        <div className="flex justify-end gap-2 mt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={deleteAccountMutation.isPending}
            className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={disabled}
            className="px-4 py-2 rounded-lg bg-error text-on-error font-semibold cursor-pointer disabled:opacity-50 hover:bg-error/90 transition-colors"
          >
            {deleteAccountMutation.isPending ? 'Deleting…' : 'Delete account'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default DangerZoneSection
