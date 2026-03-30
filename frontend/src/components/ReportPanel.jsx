import { useEffect } from 'react'

/**
 * ReportPanel
 * Props:
 *  - report: { id, title, description, status, date, location, verifiedBy }
 *  - onClose: () => void
 */
function ReportPanel({ report, onClose }) {
  // Close on Escape key
  useEffect(() => {
    function handleKey(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [onClose])

  if (!report) return null

  const isVerified = report.status === 'verified'

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/20 z-40"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Panel */}
      <aside
        className="fixed top-0 left-0 h-full w-full max-w-sm bg-white z-50 shadow-2xl flex flex-col"
        role="dialog"
        aria-modal="true"
        aria-label="Report details"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-[#e1e3e3]">
          <div className="flex items-center gap-3">
            <span
              className={`inline-flex items-center gap-1.5 text-xs font-bold px-3 py-1 rounded-full ${
                isVerified
                  ? 'bg-red-100 text-red-700'
                  : 'bg-amber-100 text-amber-700'
              }`}
            >
              <span className="material-symbols-outlined text-sm">
                {isVerified ? 'warning' : 'help'}
              </span>
              {isVerified ? 'Inaccessible' : 'Unverified'}
            </span>
          </div>
          <button
            onClick={onClose}
            className="text-[#acadad] hover:text-[#2d2f2f] transition-colors"
            aria-label="Close panel"
          >
            <span className="material-symbols-outlined text-2xl">close</span>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-6 py-6 space-y-6">
          {/* Title */}
          <h2 className="text-xl font-extrabold text-[#2d2f2f] leading-tight">
            {report.title}
          </h2>

          {/* Meta */}
          <div className="space-y-2 text-sm text-[#495f69]">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-base text-[#176a21]">location_on</span>
              <span>{report.location || 'Location not specified'}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-base text-[#176a21]">calendar_today</span>
              <span>{report.date || 'Date unknown'}</span>
            </div>
            {isVerified && report.verifiedBy && (
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-base text-[#176a21]">verified_user</span>
                <span>Verified by {report.verifiedBy}</span>
              </div>
            )}
          </div>

          {/* Description */}
          <div className="space-y-2">
            <h3 className="text-xs font-bold uppercase tracking-widest text-[#acadad]">
              Description
            </h3>
            <p className="text-sm text-[#2d2f2f] leading-relaxed">
              {report.description || 'No description provided.'}
            </p>
          </div>

          {/* Media placeholder */}
          <div className="space-y-2">
            <h3 className="text-xs font-bold uppercase tracking-widest text-[#acadad]">
              Attached Media
            </h3>
            <div className="w-full aspect-video bg-[#f0f1f1] rounded-xl flex items-center justify-center">
              <div className="text-center text-[#acadad]">
                <span className="material-symbols-outlined text-4xl">image</span>
                <p className="text-xs mt-1">No media attached</p>
              </div>
            </div>
          </div>
        </div>

        {/* Footer — Verify button */}
        {!isVerified && (
          <div className="px-6 py-5 border-t border-[#e1e3e3]">
            <button
              className="w-full py-3.5 bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-bold rounded-full shadow hover:brightness-110 transition-all"
              onClick={() => alert('Verify action — to be connected to backend')}
            >
              Verify Report
            </button>
          </div>
        )}
      </aside>
    </>
  )
}

export default ReportPanel