import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDeleteReport } from '../hooks/useUserReports.js'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import ReportObjectsList from './ReportObjectsList.jsx'

const STATUS_STYLES = {
  unverified: 'bg-yellow-100 text-yellow-900',
  verified:   'bg-green-100 text-green-900',
  rejected:   'bg-red-100 text-red-900',
  fixed:      'bg-blue-100 text-blue-900',
}

function MyReportDetailModal({ report, userId, onClose }) {
  const { t } = useTranslation()
  const [error, setError] = useState('')
  const closeButtonRef = useRef(null)
  const deleteMutation = useDeleteReport(userId)
  const navigate = useNavigate()

  useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  function handleBackdropClick(event) {
    if (event.target === event.currentTarget) onClose()
  }

  function handleEditOnMap() {
    navigate(`/?report=${report.id}`)
  }

  async function handleDelete() {
    if (!window.confirm(t('report.deleteConfirm'))) return
    try {
      await deleteMutation.mutateAsync(report.id)
      onClose()
    } catch (e) {
      setError(e.message || t('report.deleteFailed'))
    }
  }

  // Rebuild title with translated object labels, same pattern as ReportPanel.
  const displayTitle = (() => {
    if (!report.objects?.length) {
      if (report.title) return report.title
      return report.reportType === 'FEATURE'
        ? t('objectTitle.featureFallback')
        : t('objectTitle.obstacleFallback')
    }
    const labels = [...new Set(report.objects.map((o) =>
      t(`object.${o.objectType}.label`, {
        defaultValue: OBJECT_TYPE_MAP[o.objectType]?.label || o.objectType,
      })
    ))]
    return report.reportType === 'FEATURE'
      ? t('objectTitle.featureSuffix', { labels: labels.join(' & ') })
      : t('objectTitle.obstacleSuffix', { labels: labels.join(' & ') })
  })()

  const statusLabel = t(`myReports.status.${report.status}`, { defaultValue: report.status })

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t('myReportModal.ariaLabel')}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-surface-container-high rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b border-outline-variant/20">
          <h3 className="text-lg font-bold font-headline text-on-surface">{displayTitle}</h3>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label={t('myReportModal.close')}
            className="w-9 h-9 rounded-full hover:bg-surface-container flex items-center justify-center cursor-pointer"
          >
            <span className="material-symbols-outlined text-on-surface-variant">close</span>
          </button>
        </div>

        <div className="p-6 flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${STATUS_STYLES[report.status] ?? 'bg-surface-container text-on-surface'}`}>
              {statusLabel}
            </span>
            <span className="flex items-center gap-1 text-xs font-semibold text-on-surface-variant bg-surface-container px-2.5 py-1 rounded-lg">
              <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>
                {report.environment === 'INDOOR' ? 'home' : 'wb_sunny'}
              </span>
              {report.environment === 'INDOOR' ? t('report.indoor') : t('report.outdoor')}
            </span>
          </div>

          <p className="text-sm text-on-surface whitespace-pre-wrap break-words">
            {report.description || <em>{t('myReports.noDescription')}</em>}
          </p>

          <ReportObjectsList objects={report.objects} />

          {report.mediaUrls && report.mediaUrls.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {report.mediaUrls.map((url) => (
                <img
                  key={url}
                  src={url}
                  alt=""
                  className="w-24 h-24 rounded-lg object-cover"
                  onError={(e) => { e.currentTarget.style.display = 'none' }}
                />
              ))}
            </div>
          )}

          <p className="text-xs text-on-surface-variant">
            {report.date}{' · '}{report.location}
          </p>

          {error && (
            <p role="alert" className="text-sm text-error">
              {error}
            </p>
          )}

          <div className="flex gap-2 flex-wrap pt-2">
            <button
              type="button"
              onClick={handleEditOnMap}
              className="px-4 py-2 rounded-lg primary-gradient text-on-primary font-semibold cursor-pointer"
            >
              {t('myReportModal.editOnMap')}
            </button>
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="px-4 py-2 rounded-lg bg-error/10 text-error font-semibold disabled:opacity-60 cursor-pointer ml-auto"
            >
              {deleteMutation.isPending ? t('myReportModal.deleting') : t('myReportModal.delete')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default MyReportDetailModal
