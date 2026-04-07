import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { agreeReport, disagreeReport, mapReport } from '../services/reportService.js'
import { useAuth } from '../context/AuthContext.jsx'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'

/**
 * ReportPanel
 * Desktop: fixed right sidebar (500px) alongside the map.
 * Mobile: full screen overlay.
 * Props:
 *  - report: mapped report object
 *  - onClose: () => void
 *  - onVoteUpdate: (updatedReport) => void
 */
function ReportPanel({ report, userVote, onVoteChange, onClose, onVoteUpdate }) {
  const { token, isAuthenticated } = useAuth()
  const [following, setFollowing] = useState(false)
  const navigate = useNavigate()
  const [voting, setVoting] = useState(false)
  const [voteError, setVoteError] = useState('')

  useEffect(() => {
    function handleExpired() { navigate('/login') }
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [navigate])

  if (!report) return null

  const isValidated = report.status === 'verified'
  const total = (report.agrees || 0) + (report.disagrees || 0)
  const consensusPct = total > 0 ? Math.round(((report.agrees || 0) / total) * 100) : 0

  async function handleVote(type) {
    if (!token) {
      navigate('/login')
      return
    }
    setVoting(true)
    setVoteError('')
    try {
      const prevAgrees = report.agrees
      const prevDisagrees = report.disagrees
      const updated = type === 'agree'
        ? await agreeReport(report.id, token)
        : await disagreeReport(report.id, token)
      const mappedUpdated = mapReport(updated)
      onVoteUpdate(mappedUpdated)
      if (type === 'agree') {
        onVoteChange(mappedUpdated.agrees > prevAgrees ? 'agree' : null)
      } else {
        onVoteChange(mappedUpdated.disagrees > prevDisagrees ? 'disagree' : null)
      }
    } catch (err) {
      setVoteError('Failed to submit vote. Please try again.')
    } finally {
      setVoting(false)
    }
  }

  return (
    <>
      {/* Mobile backdrop */}
      <div
        className="fixed inset-0 bg-black/20 z-[1100] lg:hidden"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Panel */}
      <aside className="
        fixed top-0 right-0 h-full z-[1200]
        w-full lg:w-[500px]
        bg-surface-container-low
        overflow-y-auto
        border-l border-outline-variant/10
        flex flex-col
      ">

        {/* Report Photo */}
        <div className="p-6">
          <div className="relative">
            {report.image ? (
              <img
                className="w-full h-64 object-cover rounded-xl shadow-sm"
                src={report.image}
                alt={report.title}
              />
            ) : (
              <div className="w-full h-64 bg-surface-container rounded-xl shadow-sm flex items-center justify-center">
                <span className="material-symbols-outlined text-6xl text-outline-variant">image</span>
              </div>
            )}

            {/* Status badge */}
            <div className="absolute top-4 right-4">
              <span className={`text-xs font-bold px-3 py-1.5 rounded-full uppercase tracking-wider ${
                isValidated
                  ? 'bg-primary-container text-on-primary-container'
                  : 'bg-amber-100 text-amber-800'
              }`}>
                {isValidated ? 'Validated' : 'Unverified'}
              </span>
            </div>

            {/* Close button */}
            <button
              onClick={onClose}
              className="absolute top-4 left-4 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center shadow"
              aria-label="Close panel"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="px-8 pb-12 flex flex-col gap-8">

          {/* Header */}
          <div className="flex flex-col gap-4">
            <h1 className="text-3xl font-extrabold font-headline tracking-tight text-on-surface leading-tight">
              {report.title}
            </h1>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center">
                  <span className="material-symbols-outlined text-on-surface-variant">person</span>
                </div>
                <div>
                  <p className="text-sm font-bold text-on-surface">{report.reportedBy}</p>
                  <p className="text-xs text-on-surface-variant">{report.date}</p>
                </div>
              </div>
              {report.tags && report.tags.length > 0 && (
                <div className="flex gap-2 flex-wrap justify-end">
                  {report.tags.map(tag => {
                    const cfg = REPORT_TAGS[tag] ?? { label: tag, icon: 'warning', color: '#767777' }
                    return (
                      <span
                        key={tag}
                        className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold text-white"
                        style={{ backgroundColor: cfg.color }}
                      >
                        <span className="material-symbols-outlined leading-none" style={{ fontSize: '14px' }}>{cfg.icon}</span>
                        {cfg.label}
                      </span>
                    )
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Description */}
          <section className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10">
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              Issue Details
            </h3>
            <p className="text-on-surface leading-relaxed font-body">
              {report.description || 'No description provided.'}
            </p>
          </section>

          {/* Location */}
          {report.location && (
            <section className="bg-surface-container-lowest p-4 rounded-xl border border-outline-variant/10 flex items-center gap-3">
              <span className="material-symbols-outlined text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>
                location_on
              </span>
              <p className="text-sm font-medium text-on-surface">{report.location}</p>
            </section>
          )}

          {/* Consensus */}
          <section className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Community Consensus
              </h3>
              <span className="text-xs font-bold text-primary">{consensusPct}% Consensus</span>
            </div>
            <div className="bg-surface-container-high h-2.5 w-full rounded-full overflow-hidden">
              <div
                className="bg-gradient-to-r from-primary-container to-primary h-full rounded-full transition-all"
                style={{ width: `${consensusPct}%` }}
              />
            </div>
            <p className="text-sm text-on-surface-variant italic">
              {report.agrees || 0} people have verified this issue as active.
            </p>

            {/* Vote error */}
            {voteError && (
              <p className="text-sm text-error bg-error-container/20 rounded-lg px-4 py-2">
                {voteError}
              </p>
            )}

            <div className="grid grid-cols-2 gap-3 mt-2">
              <button
                onClick={() => handleVote('agree')}
                disabled={voting}
                aria-label="Agree"
                className={`flex items-center justify-center gap-2 py-3 rounded-xl font-bold active:scale-95 transition-all shadow-sm disabled:opacity-60 ${
                  userVote === 'agree'
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-container-highest text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'agree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_up</span>
                {voting ? '...' : 'Agree'}
              </button>
              <button
                onClick={() => handleVote('disagree')}
                disabled={voting}
                aria-label="Disagree"
                className={`flex items-center justify-center gap-2 py-3 rounded-xl font-bold active:scale-95 transition-all disabled:opacity-60 ${
                  userVote === 'disagree'
                    ? 'bg-error text-white'
                    : 'bg-surface-container-highest text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'disagree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_down</span>
                {voting ? '...' : 'Disagree'}
              </button>
            </div>
          </section>

          {/* Activity Timeline */}
          <section className="flex flex-col gap-6">
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
              Recent Activity
            </h3>
            <div className="relative pl-6 flex flex-col gap-8">
              <div className="absolute left-[7px] top-2 bottom-2 w-[2px] bg-surface-container-highest" />
              <div className="relative">
                <div className="absolute -left-[23px] top-1 w-4 h-4 rounded-full bg-primary ring-4 ring-surface-container-low" />
                <div className="flex flex-col gap-1">
                  <p className="text-sm font-bold text-on-surface">System</p>
                  <p className="text-sm text-on-surface-variant">
                    Report submitted and pending community verification.
                  </p>
                  <p className="text-xs text-outline mt-1 uppercase font-bold">{report.date}</p>
                </div>
              </div>
            </div>
          </section>

          {/* Follow Updates */}
          <div className="pt-4">
            <button
              onClick={() => {
                if (!isAuthenticated) { navigate('/login'); return }
                setFollowing(prev => !prev)
              }}
              className={`w-full py-5 rounded-xl font-extrabold text-lg font-headline shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-3 ${
                following
                  ? 'bg-surface-container-high text-on-surface border border-outline-variant/20'
                  : 'bg-gradient-to-b from-primary to-primary-dim text-on-primary'
              }`}
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>
                {following ? 'notifications_off' : 'notifications_active'}
              </span>
              {following ? 'Unfollow' : 'Follow Updates'}
            </button>
            <p className="text-center text-xs text-on-surface-variant mt-4 px-6">
              {following
                ? 'You will be notified of every status change on this report.'
                : 'Follow to receive notifications when this report status changes.'}
            </p>
          </div>

        </div>
      </aside>
    </>
  )
}

export default ReportPanel