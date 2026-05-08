import { useState } from 'react'
import { useAuth } from '../../context/AuthContext.jsx'
import {
  useAdminUsersQuery,
  useBanUser,
  useChangeUserRole,
  useDeleteAdminUser,
  useUnbanUser,
} from '../../hooks/useAdmin.js'
import Badge from '../../components/admin/Badge.jsx'
import ConfirmDialog from '../../components/admin/ConfirmDialog.jsx'
import CreateUserModal from '../../components/admin/CreateUserModal.jsx'
import DataTable from '../../components/admin/DataTable.jsx'
import Pagination from '../../components/admin/Pagination.jsx'

function formatDate(value) {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

const STATUS_TONE = { ACTIVE: 'success', BANNED: 'danger' }
const ROLE_TONE = { ADMIN: 'info', USER: 'neutral' }

function AdminUsersPage() {
  const { userId } = useAuth()
  const [filters, setFilters] = useState({ status: '', role: '', page: 0, size: 20 })
  const [confirm, setConfirm] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [error, setError] = useState(null)

  const query = useAdminUsersQuery(filters)
  const banMutation = useBanUser()
  const unbanMutation = useUnbanUser()
  const roleMutation = useChangeUserRole()
  const deleteMutation = useDeleteAdminUser()

  function updateFilter(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value, page: 0 }))
  }

  function handlePageChange(page) {
    setFilters((prev) => ({ ...prev, page }))
  }

  function runMutation(mutation, args, label) {
    setError(null)
    mutation.mutate(args, {
      onError: (err) => setError(err.message || `Failed to ${label}`),
      onSettled: () => setConfirm(null),
    })
  }

  function askBan(user) {
    setConfirm({
      kind: 'ban',
      title: `Ban ${user.name}?`,
      message: 'They will be unable to log in or interact with the platform until unbanned.',
      destructive: true,
      confirmLabel: 'Ban',
      onConfirm: () => runMutation(banMutation, user.id, 'ban user'),
    })
  }

  function askDelete(user) {
    setConfirm({
      kind: 'delete',
      title: `Delete ${user.name}?`,
      message: 'This permanently removes the account and all linked content. This cannot be undone.',
      destructive: true,
      confirmLabel: 'Delete',
      onConfirm: () => runMutation(deleteMutation, user.id, 'delete user'),
    })
  }

  function toggleRole(user) {
    const role = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
    setConfirm({
      kind: 'role',
      title: `Change role to ${role}?`,
      message: `${user.name} will be ${role === 'ADMIN' ? 'granted' : 'stripped of'} admin privileges.`,
      destructive: false,
      confirmLabel: `Make ${role}`,
      onConfirm: () => runMutation(roleMutation, { id: user.id, role }, 'change role'),
    })
  }

  const page = query.data
  const rows = page?.content ?? []
  const totalPages = page?.totalPages ?? 1
  const busy =
    banMutation.isPending ||
    unbanMutation.isPending ||
    deleteMutation.isPending ||
    roleMutation.isPending

  const columns = [
    { key: 'id', label: 'ID', width: 64 },
    { key: 'name', label: 'Name' },
    { key: 'email', label: 'Email' },
    {
      key: 'role',
      label: 'Role',
      render: (row) => <Badge tone={ROLE_TONE[row.role]}>{row.role}</Badge>,
    },
    {
      key: 'status',
      label: 'Status',
      render: (row) => <Badge tone={STATUS_TONE[row.status]}>{row.status}</Badge>,
    },
    {
      key: 'registeredAt',
      label: 'Registered',
      render: (row) => <span className="text-on-surface-variant">{formatDate(row.registeredAt)}</span>,
    },
    {
      key: 'points',
      label: 'Points',
      render: (row) => <span className="tabular-nums">{row.points}</span>,
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (row) => {
        const isSelf = userId != null && Number(userId) === Number(row.id)
        return (
          <div className="flex flex-wrap gap-2">
            {row.status === 'BANNED' ? (
              <button
                type="button"
                onClick={() => runMutation(unbanMutation, row.id, 'unban user')}
                disabled={busy}
                className="px-2 py-1 rounded-md text-xs font-semibold bg-primary-container text-on-primary-container hover:opacity-90 disabled:opacity-50"
              >
                Unban
              </button>
            ) : (
              <button
                type="button"
                onClick={() => askBan(row)}
                disabled={busy || isSelf}
                title={isSelf ? "You can't ban yourself" : 'Ban user'}
                className="px-2 py-1 rounded-md text-xs font-semibold bg-error-container text-on-error-container hover:opacity-90 disabled:opacity-50"
              >
                Ban
              </button>
            )}
            <button
              type="button"
              onClick={() => toggleRole(row)}
              disabled={busy}
              className="px-2 py-1 rounded-md text-xs font-semibold bg-secondary-container text-on-secondary-container hover:opacity-90 disabled:opacity-50"
            >
              Make {row.role === 'ADMIN' ? 'USER' : 'ADMIN'}
            </button>
            <button
              type="button"
              onClick={() => askDelete(row)}
              disabled={busy || isSelf}
              title={isSelf ? "You can't delete yourself" : 'Delete user'}
              className="px-2 py-1 rounded-md text-xs font-semibold border border-error text-error hover:bg-error-container disabled:opacity-50"
            >
              Delete
            </button>
          </div>
        )
      },
    },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">Users</h1>
          <p className="text-on-surface-variant mt-1">Ban, unban, change roles, or remove users.</p>
        </div>
        <button
          type="button"
          onClick={() => setCreateOpen(true)}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-white font-semibold hover:bg-primary-dim transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]">person_add</span>
          New User
        </button>
      </header>

      <div className="flex flex-wrap gap-3 items-end">
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Status</span>
          <select
            value={filters.status}
            onChange={(e) => updateFilter('status', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="BANNED">Banned</option>
          </select>
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Role</span>
          <select
            value={filters.role}
            onChange={(e) => updateFilter('role', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            <option value="USER">User</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
      </div>

      {error && (
        <div className="rounded-xl bg-error-container text-on-error-container px-4 py-3">{error}</div>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.id}
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        emptyLabel="No users match these filters"
      />

      <Pagination page={filters.page} totalPages={totalPages} onPageChange={handlePageChange} />

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.confirmLabel}
        destructive={confirm?.destructive}
        busy={busy}
        onCancel={() => setConfirm(null)}
        onConfirm={() => confirm?.onConfirm?.()}
      />

      <CreateUserModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  )
}

export default AdminUsersPage
