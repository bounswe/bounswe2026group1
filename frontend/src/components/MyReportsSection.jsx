import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useUserReports } from '../hooks/useUserReports.js'
import MyReportDetailModal from './MyReportDetailModal.jsx'

const STATUS_STYLES = {
  unverified: 'bg-yellow-100 text-yellow-900',
  verified:   'bg-green-100 text-green-900',
  rejected:   'bg-red-100 text-red-900',
  fixed:      'bg-blue-100 text-blue-900',
}

// Status labels live under myReports.status.* in the locale JSON; we look them
// up at render time so the active language flips with i18n.

function truncate(text, max = 120) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function MyReportsSection({ userId, isOwnProfile = true }) {
  const { t } = useTranslation()
  const { data: reports, isPending, isError, error } = useUserReports(userId)
  const [selectedId, setSelectedId] = useState(null)

  const selected = reports?.find((r) => r.id === selectedId) ?? null

  return (
    <section className="bg-surface-container-lowest rounded-2xl shadow-sm p-6">
      <h2 className="text-xl font-bold font-headline text-on-surface mb-4">
        {isOwnProfile ? t('myReports.headingOwn') : t('myReports.headingOther')}{reports ? ` (${reports.length})` : ''}
      </h2>

      {isPending && (
        <p className="text-sm text-on-surface-variant">{t('myReports.loading')}</p>
      )}

      {isError && (
        <p role="alert" className="text-sm text-error">
          {t('myReports.loadFailed')}{error?.message ? `: ${error.message}` : '.'}
        </p>
      )}

      {!isPending && !isError && reports?.length === 0 && (
        <div className="text-sm text-on-surface-variant">
          {isOwnProfile ? (
            <>
              {t('myReports.emptyOwnPrefix')}{' '}
              <Link to="/" className="text-primary font-semibold hover:underline">
                {t('myReports.addOneOnMap')}
              </Link>
            </>
          ) : (
            t('myReports.emptyOther')
          )}
        </div>
      )}

      {!isPending && !isError && reports && reports.length > 0 && (
        <ul className="flex flex-col gap-3">
          {reports.map((report) => (
            <li key={report.id}>
              <button
                type="button"
                onClick={() => setSelectedId(report.id)}
                className="w-full flex gap-3 items-start text-left p-3 rounded-xl bg-surface-container hover:bg-surface-container-high transition-colors cursor-pointer"
              >
                {report.image ? (
                  <img
                    src={report.image}
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
                    <span className="text-sm font-semibold text-on-surface">
                      {report.title}
                    </span>
                    <span
                      className={`text-xs font-bold px-2 py-0.5 rounded-full ${STATUS_STYLES[report.status] ?? 'bg-surface-container text-on-surface'}`}
                    >
                      {t(`myReports.status.${report.status}`, { defaultValue: report.status })}
                    </span>
                  </div>
                  <p className="text-sm text-on-surface-variant break-words">
                    {truncate(report.description) || <em>{t('myReports.noDescription')}</em>}
                  </p>
                  <p className="text-xs text-on-surface-variant mt-1">
                    {report.date}
                  </p>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <MyReportDetailModal
          key={selected.id}
          report={selected}
          userId={userId}
          onClose={() => setSelectedId(null)}
        />
      )}
    </section>
  )
}

export default MyReportsSection
