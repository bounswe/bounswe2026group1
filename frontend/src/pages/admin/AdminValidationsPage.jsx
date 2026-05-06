import { useState } from 'react'
import { useAdminValidationsQuery, useDeleteAdminValidation } from '../../hooks/useAdmin.js'
import Badge from '../../components/admin/Badge.jsx'
import ConfirmDialog from '../../components/admin/ConfirmDialog.jsx'
import DataTable from '../../components/admin/DataTable.jsx'
import Pagination from '../../components/admin/Pagination.jsx'

const VOTE_TONE = { AGREE: 'success', DISAGREE: 'danger' }

function AdminValidationsPage() {
  const [filters, setFilters] = useState({
    reportId: '',
    userId: '',
    voteType: '',
    page: 0,
    size: 20,
  })
  const [confirm, setConfirm] = useState(null)
  const [error, setError] = useState(null)

  const queryParams = {
    ...filters,
    reportId: filters.reportId || undefined,
    userId: filters.userId || undefined,
    voteType: filters.voteType || undefined,
  }
  const query = useAdminValidationsQuery(queryParams)
  const deleteMutation = useDeleteAdminValidation()

  function updateFilter(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value, page: 0 }))
  }

  function askDelete(validation) {
    setConfirm({
      title: `Delete validation #${validation.id}?`,
      message: "This removes the user's vote on this report.",
      onConfirm: () => {
        setError(null)
        deleteMutation.mutate(validation.id, {
          onError: (err) => setError(err.message || 'Failed to delete validation'),
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
    { key: 'userName', label: 'User' },
    {
      key: 'reportId',
      label: 'Report',
      render: (row) => <span className="text-on-surface-variant">#{row.reportId}</span>,
    },
    {
      key: 'voteType',
      label: 'Vote',
      render: (row) => <Badge tone={VOTE_TONE[row.voteType]}>{row.voteType}</Badge>,
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
        <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">Validations</h1>
        <p className="text-on-surface-variant mt-1">Inspect and remove individual agree/disagree votes.</p>
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
          <span className="block text-xs font-medium text-on-surface-variant mb-1">User ID</span>
          <input
            type="number"
            min="0"
            value={filters.userId}
            onChange={(e) => updateFilter('userId', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
            placeholder="any"
          />
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Vote</span>
          <select
            value={filters.voteType}
            onChange={(e) => updateFilter('voteType', e.target.value)}
            className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            <option value="AGREE">Agree</option>
            <option value="DISAGREE">Disagree</option>
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
        emptyLabel="No validations match these filters"
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

export default AdminValidationsPage
