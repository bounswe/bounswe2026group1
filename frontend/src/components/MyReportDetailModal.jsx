import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDeleteReport } from '../hooks/useUserReports.js'

function formatDateTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function MyReportDetailModal({ report, userId, onClose }) {
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
    navigate(`/?report=${report.reportId}`)
  }

  async function handleDelete() {
    if (!window.confirm('Delete this report? This cannot be undone.')) return
    try {
      await deleteMutation.mutateAsync(report.reportId)
      onClose()
    } catch (e) {
      setError(e.message || 'Failed to delete report.')
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Report details"
      onClick={handleBackdropClick}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b border-outline-variant/20">
          <h3 className="text-lg font-bold font-headline text-on-surface">Report details</h3>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="w-9 h-9 rounded-full hover:bg-surface-container flex items-center justify-center cursor-pointer"
          >
            <span className="material-symbols-outlined text-on-surface-variant">close</span>
          </button>
        </div>

        <div className="p-6 flex flex-col gap-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-surface-container text-on-surface">
              {report.status}
            </span>
            <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant">
              {report.reportType}
            </span>
            <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant">
              {report.environment}
            </span>
          </div>

          <p className="text-sm text-on-surface whitespace-pre-wrap break-words">
            {report.description || <em>(no description)</em>}
          </p>

          {report.objects && report.objects.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {report.objects.map((obj, idx) => (
                <span
                  key={idx}
                  className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary font-semibold capitalize"
                >
                  {obj.objectType?.replaceAll('_', ' ').toLowerCase()}
                </span>
              ))}
            </div>
          )}

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
            {formatDateTime(report.publishDate)}{' · '}
            {report.latitude?.toFixed(4)}, {report.longitude?.toFixed(4)}
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
              className="px-4 py-2 rounded-lg bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-semibold cursor-pointer"
            >
              Edit on map
            </button>
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="px-4 py-2 rounded-lg bg-error/10 text-error font-semibold disabled:opacity-60 cursor-pointer ml-auto"
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default MyReportDetailModal
