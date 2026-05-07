import { useState } from 'react'
import {
  useAdminReportsQuery,
  useChangeReportStatus,
  useDeleteAdminReport,
} from '../../hooks/useAdmin.js'
import Badge from '../../components/admin/Badge.jsx'
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

function toIso(local) {
  if (!local) return undefined
  const d = new Date(local)
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString()
}

const STATUSES = ['PENDING', 'VERIFIED', 'REJECTED']

function AdminReportsPage() {
  const [filters, setFilters] = useState({
    status: '',
    environment: '',
    type: '',
    categoryId: '',
    dateFrom: '',
    dateTo: '',
    page: 0,
    size: 20,
  })
  const [confirm, setConfirm] = useState(null)
  const [error, setError] = useState(null)

  const queryParams = {
    ...filters,
    categoryId: filters.categoryId || undefined,
    dateFrom: toIso(filters.dateFrom),
    dateTo: toIso(filters.dateTo),
  }
  const query = useAdminReportsQuery(queryParams)
  const statusMutation = useChangeReportStatus()
  const deleteMutation = useDeleteAdminReport()

  function updateFilter(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value, page: 0 }))
  }

  function handleStatusChange(report, status) {
    if (status === report.status) return
    setError(null)
    statusMutation.mutate(
      { id: report.reportId, status },
      { onError: (err) => setError(err.message || 'Failed to change status') },
    )
  }

  function askDelete(report) {
    setConfirm({
      title: `Delete report #${report.reportId}?`,
      message: 'This permanently removes the report along with its comments and validations.',
      onConfirm: () => {
        setError(null)
        deleteMutation.mutate(report.reportId, {
          onError: (err) => setError(err.message || 'Failed to delete report'),
          onSettled: () => setConfirm(null),
        })
      },
    })
  }

  const page = query.data
  const rows = page?.content ?? []
  const totalPages = page?.totalPages ?? 1
  const busy = statusMutation.isPending || deleteMutation.isPending

  const columns = [
    { key: 'reportId', label: 'ID', width: 64 },
    { key: 'categoryName', label: 'Category' },
    {
      key: 'categoryType',
      label: 'Type',
      render: (row) => (
        <Badge tone={row.categoryType === 'FEATURE' ? 'success' : 'neutral'}>
          {row.categoryType ?? '—'}
        </Badge>
      ),
    },
    {
      key: 'environment',
      label: 'Env',
      render: (row) => (row.environment ? <Badge tone="info">{row.environment}</Badge> : '—'),
    },
    {
      key: 'status',
      label: 'Status',
      render: (row) => (
        <select
          value={row.status}
          disabled={busy}
          onChange={(e) => handleStatusChange(row, e.target.value)}
          className={[
            'px-2 py-1 rounded-lg border text-xs font-semibold',
            'bg-surface-container-lowest border-outline-variant',
          ].join(' ')}
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      ),
    },
    {
      key: 'votes',
      label: 'Votes',
      render: (row) => (
        <span className="tabular-nums text-sm">
          <span className="text-primary font-semibold">{row.agrees}</span>
          {' / '}
          <span className="text-error font-semibold">{row.disagrees}</span>
        </span>
      ),
    },
    {
      key: 'publishDate',
      label: 'Published',
      render: (row) => <span className="text-on-surface-variant">{formatDate(row.publishDate)}</span>,
    },
    { key: 'userId', label: 'User', render: (row) => <span>#{row.userId}</span> },
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
        <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">Reports</h1>
        <p className="text-on-surface-variant mt-1">
          Filter, change status, and delete reports across the platform.
        </p>
      </header>

      <div className="grid gap-3 grid-cols-2 md:grid-cols-3 lg:grid-cols-6">
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Status</span>
          <select
            value={filters.status}
            onChange={(e) => updateFilter('status', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Environment</span>
          <select
            value={filters.environment}
            onChange={(e) => updateFilter('environment', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            <option value="INDOOR">Indoor</option>
            <option value="OUTDOOR">Outdoor</option>
          </select>
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Type</span>
          <select
            value={filters.type}
            onChange={(e) => updateFilter('type', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          >
            <option value="">All</option>
            <option value="OBSTACLE">Obstacle</option>
            <option value="FEATURE">Feature</option>
          </select>
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">Category ID</span>
          <input
            type="number"
            min="0"
            value={filters.categoryId}
            onChange={(e) => updateFilter('categoryId', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
            placeholder="any"
          />
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">From</span>
          <input
            type="datetime-local"
            value={filters.dateFrom}
            onChange={(e) => updateFilter('dateFrom', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          />
        </label>
        <label className="block">
          <span className="block text-xs font-medium text-on-surface-variant mb-1">To</span>
          <input
            type="datetime-local"
            value={filters.dateTo}
            onChange={(e) => updateFilter('dateTo', e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest"
          />
        </label>
      </div>

      {error && (
        <div className="rounded-xl bg-error-container text-on-error-container px-4 py-3">{error}</div>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.reportId}
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        emptyLabel="No reports match these filters"
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

export default AdminReportsPage
