import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useUserReports } from '../hooks/useUserReports.js'
import MyReportDetailModal from './MyReportDetailModal.jsx'

const STATUS_STYLES = {
  PENDING: 'bg-yellow-100 text-yellow-900',
  VERIFIED: 'bg-green-100 text-green-900',
  REJECTED: 'bg-red-100 text-red-900',
  FIXED: 'bg-blue-100 text-blue-900',
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
  })
}

function truncate(text, max = 120) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function reportLabel(report) {
  const objectType = report.objects?.[0]?.objectType
  if (objectType) return objectType.replaceAll('_', ' ').toLowerCase()
  return report.reportType?.toLowerCase() ?? 'report'
}

function MyReportsSection({ userId }) {
  const { data: reports, isPending, isError, error } = useUserReports(userId)
  const [selectedId, setSelectedId] = useState(null)

  const selected = reports?.find((r) => r.reportId === selectedId) ?? null

  return (
    <section className="bg-white rounded-2xl shadow-sm p-6">
      <h2 className="text-xl font-bold font-headline text-on-surface mb-4">
        My Reports{reports ? ` (${reports.length})` : ''}
      </h2>

      {isPending && (
        <p className="text-sm text-on-surface-variant">Loading your reports…</p>
      )}

      {isError && (
        <p role="alert" className="text-sm text-error">
          Failed to load reports{error?.message ? `: ${error.message}` : '.'}
        </p>
      )}

      {!isPending && !isError && reports?.length === 0 && (
        <div className="text-sm text-on-surface-variant">
          You haven't submitted any reports yet.{' '}
          <Link to="/" className="text-primary font-semibold hover:underline">
            Add one on the map.
          </Link>
        </div>
      )}

      {!isPending && !isError && reports && reports.length > 0 && (
        <ul className="flex flex-col gap-3">
          {reports.map((report) => (
            <li key={report.reportId}>
              <button
                type="button"
                onClick={() => setSelectedId(report.reportId)}
                className="w-full flex gap-3 items-start text-left p-3 rounded-xl bg-surface-container-low hover:bg-surface-container transition-colors cursor-pointer"
              >
                {report.mediaUrls?.[0] ? (
                  <img
                    src={report.mediaUrls[0]}
                    alt=""
                    className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                    onError={(e) => { e.currentTarget.style.display = 'none' }}
                  />
                ) : (
                  <div className="w-16 h-16 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0">
                    <span className="material-symbols-outlined text-on-surface-variant">image</span>
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <span className="text-sm font-semibold text-on-surface capitalize">
                      {reportLabel(report)}
                    </span>
                    <span
                      className={`text-xs font-bold px-2 py-0.5 rounded-full ${STATUS_STYLES[report.status] ?? 'bg-gray-100 text-gray-900'}`}
                    >
                      {report.status}
                    </span>
                  </div>
                  <p className="text-sm text-on-surface-variant break-words">
                    {truncate(report.description) || <em>(no description)</em>}
                  </p>
                  <p className="text-xs text-on-surface-variant mt-1">
                    {formatDate(report.publishDate)}
                  </p>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <MyReportDetailModal
          key={selected.reportId}
          report={selected}
          userId={userId}
          onClose={() => setSelectedId(null)}
        />
      )}
    </section>
  )
}

export default MyReportsSection
