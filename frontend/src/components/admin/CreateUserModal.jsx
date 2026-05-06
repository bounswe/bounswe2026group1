import { useState } from 'react'
import { useCreateAdminUser } from '../../hooks/useAdmin.js'

const initialForm = { email: '', password: '', name: '', role: 'USER' }

function CreateUserModal({ open, onClose }) {
  const [form, setForm] = useState(initialForm)
  const [error, setError] = useState(null)
  const createUser = useCreateAdminUser()

  if (!open) return null

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    createUser.mutate(form, {
      onSuccess: () => {
        setForm(initialForm)
        onClose()
      },
      onError: (err) => setError(err.message || 'Failed to create user'),
    })
  }

  function handleCancel() {
    if (createUser.isPending) return
    setForm(initialForm)
    setError(null)
    onClose()
  }

  return (
    <div
      className="fixed inset-0 z-[1100] flex items-center justify-center p-4 bg-inverse-surface/40 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-user-title"
    >
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-2xl bg-surface-container-lowest shadow-xl p-6"
      >
        <h2 id="create-user-title" className="text-lg font-semibold font-headline text-on-surface mb-4">
          Create User
        </h2>
        <div className="space-y-3">
          <label className="block">
            <span className="text-sm font-medium text-on-surface-variant">Name</span>
            <input
              type="text"
              required
              minLength={2}
              maxLength={50}
              value={form.name}
              onChange={(e) => update('name', e.target.value)}
              className="mt-1 w-full px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-on-surface-variant">Email</span>
            <input
              type="email"
              required
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              className="mt-1 w-full px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-on-surface-variant">Password</span>
            <input
              type="password"
              required
              minLength={8}
              value={form.password}
              onChange={(e) => update('password', e.target.value)}
              className="mt-1 w-full px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:ring-2 focus:ring-primary"
            />
            <span className="block text-xs text-on-surface-variant mt-1">
              Min 8 chars, with upper, lower, digit, and special character.
            </span>
          </label>
          <label className="block">
            <span className="text-sm font-medium text-on-surface-variant">Role</span>
            <select
              value={form.role}
              onChange={(e) => update('role', e.target.value)}
              className="mt-1 w-full px-3 py-2 rounded-lg border border-outline-variant focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </label>
        </div>
        {error && <div className="mt-4 text-sm text-error">{error}</div>}
        <div className="flex justify-end gap-2 mt-6">
          <button
            type="button"
            onClick={handleCancel}
            disabled={createUser.isPending}
            className="px-4 py-2 rounded-lg text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={createUser.isPending}
            className="px-4 py-2 rounded-lg bg-primary hover:bg-primary-dim text-white font-semibold transition-colors disabled:opacity-50"
          >
            {createUser.isPending ? 'Creating…' : 'Create'}
          </button>
        </div>
      </form>
    </div>
  )
}

export default CreateUserModal
