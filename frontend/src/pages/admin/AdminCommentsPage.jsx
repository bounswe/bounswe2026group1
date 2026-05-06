import { useState } from 'react'
import { useAdminCommentsQuery, useDeleteAdminComment } from '../../hooks/useAdmin.js'
import ConfirmDialog from '../../components/admin/ConfirmDialog.jsx'
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

function AdminCommentsPage() {
  const [filters, setFilters] = useState({ reportId: '', authorId: '', page: 0, size: 20 })
  const [confirm, setConfirm] = useState(null)
  const [error, setError] = useState(null)

  const queryParams = {
    ...filters,
    reportId: filters.reportId || undefined,
    authorId: filters.authorId || undefined,
  }
  const query = useAdminCommentsQuery(queryParams)
  const deleteMutation = useDeleteAdminComment()

  function updateFilter(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value, page: 0 }))
  }

  function askDelete(comment) {
    setConfirm({
      title: `Delete comment #${comment.id}?`,
      message: 'This permanently removes the comment.',
      onConfirm: () => {
        setError(null)
        deleteMutation.mutate(comment.id, {
          onError: (err) => setError(err.message || 'Failed to delete comment'),
          onSettled: () => setConfirm(null),
        })
      },
    })
  }

  const page = query.data
  const rows = page?.content ?? []
  const totalPages = page?.totalPages ?? 1
  const busy = deleteMutation.isPending

  const columns = [
    { key: 'id', label: 'ID', width: 64 },
    { key: 'authorName', label: 'Author' },
    {
      key: 'reportId',
      label: 'Report',
      render: (row) => <span className="text-on-surface-variant">#{row.reportId}</span>,
    },
    {
      key: 'content',
      label: 'Content',
      render: (row) => (
        <span className="text-on-surface line-clamp-2 break-words">{row.content}</span>
      ),
    },
    {
      key: 'createdAt',
      label: 'Created',
      render: (row) => <span className="text-on-surface-variant">{formatDate(row.createdAt)}</span>,
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (row) => (
        <button
          type="button"
          onClick={() => askDelete(row)}
          disabled={busy}
          className="px-2 py-1 rounded-md text-xs font-semibold border border-error text-error hover:bg-error-container disabled:opacity-50"
        >
          Delete
        </button>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">Comments</h1>
        <p className="text-on-surface-variant mt-1">Moderate comments by report or author.</p>
      </header>

      <div className="flex flex-wrap gap-3">
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Report ID</span>
          <input
            type="number"
            min="0"
            value={filters.reportId}
            onChange={(e) => updateFilter('reportId', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
            placeholder="any"
          />
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Author ID</span>
          <input
            type="number"
            min="0"
            value={filters.authorId}
            onChange={(e) => updateFilter('authorId', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
            placeholder="any"
          />
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
        emptyLabel="No comments match these filters"
      />

      <Pagination
        page={filters.page}
        totalPages={totalPages}
        onPageChange={(p) => setFilters((prev) => ({ ...prev, page: p }))}
      />

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel="Delete"
        destructive
        busy={busy}
        onCancel={() => setConfirm(null)}
        onConfirm={() => confirm?.onConfirm?.()}
      />
    </div>
  )
}

export default AdminCommentsPage
