import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'

import { agreeReport, disagreeReport, mapReport, getCommentsByReport, createComment, deleteComment } from '../services/reportService.js'
import { useMutation } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'

/**
 * ReportPanel
 * Desktop: fixed right sidebar (500px) alongside the map.
 * Mobile: 60dvh bottom sheet allowing map interaction.
 * Props:
 * - report: mapped report object
 * - onClose: () => void
 * - onVoteUpdate: (updatedReport) => void
 */
function ReportPanel({ report, userVote, onVoteChange, onClose, onVoteUpdate, onFollowChange }) {

  const { token, isAuthenticated, userId } = useAuth()
  const navigate = useNavigate()
  const [voteError, setVoteError] = useState('')
  const [comments, setComments] = useState([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [newComment, setNewComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [following, setFollowing] = useState(false)

  // Bottom-sheet drag-to-resize (mobile only — desktop layout uses lg: utilities to ignore this).
  // Snap points in dvh; below DISMISS_THRESHOLD on release we call onClose().
  const SHEET_SNAP_POINTS = [25, 60, 90]
  const SHEET_DISMISS_THRESHOLD = 15
  const SHEET_DEFAULT_DVH = 60
  const [sheetHeightDvh, setSheetHeightDvh] = useState(SHEET_DEFAULT_DVH)
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef({ startY: 0, startHeight: SHEET_DEFAULT_DVH, active: false })

  function handleHandlePointerDown(e) {
    // Capture pointer so subsequent move/up events fire on this element even
    // if the finger leaves the handle area.
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = { startY: e.clientY, startHeight: sheetHeightDvh, active: true }
    setIsDragging(true)
  }

  function handleHandlePointerMove(e) {
    if (!dragRef.current.active) return
    const deltaY = e.clientY - dragRef.current.startY
    // Dragging up (smaller clientY → negative deltaY) grows the sheet.
    const dvhDelta = (deltaY / window.innerHeight) * 100
    const next = dragRef.current.startHeight - dvhDelta
    setSheetHeightDvh(Math.max(5, Math.min(95, next)))
  }

  function handleHandlePointerUp(e) {
    if (!dragRef.current.active) return
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch { /* noop */ }
    dragRef.current.active = false
    setIsDragging(false)
    setSheetHeightDvh(prev => {
      if (prev < SHEET_DISMISS_THRESHOLD) {
        onClose()
        return SHEET_DEFAULT_DVH // reset for next open
      }
      // Snap to the nearest predefined point.
      return SHEET_SNAP_POINTS.reduce(
        (best, p) => (Math.abs(p - prev) < Math.abs(best - prev) ? p : best),
        SHEET_SNAP_POINTS[0],
      )
    })
  }

  useEffect(() => {
    if (!report) return
    setCommentsLoading(true)
    getCommentsByReport(report.id, token)
      .then(data => setComments(Array.isArray(data) ? data : (data?.content ?? data?.comments ?? [])))
      .catch(err => { console.error('[ReportPanel] Failed to load comments:', err); setComments([]) })
      .finally(() => setCommentsLoading(false))
  }, [report?.id])

  const voteMutation = useMutation({
    mutationFn: ({ type }) =>
      type === 'agree'
        ? agreeReport(report.id, token)
        : disagreeReport(report.id, token),
    onSuccess: (updated) => {
      setVoteError('')
      const mappedUpdated = mapReport(updated)
      onVoteUpdate(mappedUpdated)
      onVoteChange(mappedUpdated.userVote ?? null)
    },
    onError: () => {
      setVoteError('Failed to submit vote. Please try again.')
    },
  })

  function handleVote(type) {
    if (!token) { navigate('/login'); return }
    setVoteError('')
    voteMutation.mutate({ type })
  }

  if (!report) return null

  const isValidated = report.status === 'verified'
  const total = (report.agrees || 0) + (report.disagrees || 0)
  const consensusPct = total > 0 ? Math.round(((report.agrees || 0) / total) * 100) : 0
  const voting = voteMutation.isPending

  async function handleCommentSubmit() {
    if (!newComment.trim()) return
    setSubmittingComment(true)
    try {
      console.log('[handleCommentSubmit] Submitting comment:', newComment.trim())
      const created = await createComment(report.id, newComment.trim(), token, userId)
      console.log('[handleCommentSubmit] Created comment:', created)
      setComments(prev => [created, ...prev])
      setNewComment('')
    } catch (err) {
      console.error('[handleCommentSubmit] Error submitting comment:', err)
    } finally {
      setSubmittingComment(false)
    }
  }

  async function handleDeleteComment(commentId) {
    try {
      await deleteComment(commentId, token)
      setComments(prev => prev.filter(c => c.id !== commentId))
    } catch (err) {
      console.error('Failed to delete comment', err)
    }
  }

  function formatDate(iso) {
    try {
      return new Date(iso).toLocaleDateString('en-GB', {
        day: 'numeric', month: 'short', year: 'numeric'
      })
    } catch {
      return iso
    }
  }

  return (
    <div className="fixed inset-0 z-[1200] pointer-events-none flex flex-col justify-end lg:flex-row lg:justify-end">
      <aside
        // CSS variable so Tailwind's lg: utilities can still override height on desktop.
        style={{ '--sheet-h': `${sheetHeightDvh}dvh` }}
        className={`pointer-events-auto w-full h-[var(--sheet-h)] max-h-[var(--sheet-h)] lg:h-full lg:max-h-full lg:w-[500px] bg-surface-container-low flex flex-col rounded-t-[32px] lg:rounded-none border-t border-outline-variant/20 lg:border-t-0 lg:border-l shadow-[0_-10px_40px_rgba(0,0,0,0.2)] lg:shadow-none relative ${isDragging ? '' : 'transition-[height,max-height] duration-200 ease-out'}`}
      >

        {/* Mobile drag handle — pill is decorative, the wider hit-area below carries the pointer events. */}
        <div
          role="slider"
          aria-label="Resize report panel"
          aria-valuemin={SHEET_SNAP_POINTS[0]}
          aria-valuemax={SHEET_SNAP_POINTS[SHEET_SNAP_POINTS.length - 1]}
          aria-valuenow={Math.round(sheetHeightDvh)}
          tabIndex={-1}
          onPointerDown={handleHandlePointerDown}
          onPointerMove={handleHandlePointerMove}
          onPointerUp={handleHandlePointerUp}
          onPointerCancel={handleHandlePointerUp}
          className="lg:hidden flex-shrink-0 pt-3 pb-2 cursor-grab active:cursor-grabbing touch-none select-none"
        >
          <div className="w-12 h-1.5 bg-outline-variant/40 rounded-full mx-auto" />
        </div>

        <div className="flex-1 overflow-y-auto">
          <div className="p-6 pt-2 lg:pt-6">
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

              <div className="absolute top-4 right-4">
                <span className={`text-xs font-bold px-3 py-1.5 rounded-full uppercase tracking-wider ${
                  isValidated
                    ? 'bg-primary-container text-on-primary-container'
                    : 'bg-amber-100 text-amber-800'
                }`}>
                  {isValidated ? 'Validated' : 'Unverified'}
                </span>
              </div>

              <button
                onClick={onClose}
                className="absolute top-4 left-4 w-8 h-8 bg-white/90 hover:bg-white rounded-full flex items-center justify-center shadow transition-colors"
                aria-label="Close panel"
              >
                <span className="material-symbols-outlined text-base">close</span>
              </button>
            </div>
          </div>

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
              <div className="flex items-center justify-between text-sm text-on-surface-variant italic">
                <span>{report.agrees || 0} people have agreed that this issue is active.</span>
                <span className="flex gap-3 not-italic font-semibold">
                  <span className="flex items-center gap-1 text-primary">
                    <span className="material-symbols-outlined" style={{ fontSize: '16px', fontVariationSettings: "'FILL' 1" }}>thumb_up</span>
                    {report.agrees || 0}
                  </span>
                  <span className="flex items-center gap-1 text-error">
                    <span className="material-symbols-outlined" style={{ fontSize: '16px', fontVariationSettings: "'FILL' 1" }}>thumb_down</span>
                    {report.disagrees || 0}
                  </span>
                </span>
              </div>

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
                      : 'bg-surface-container-highest text-on-surface hover:bg-primary/10 hover:text-primary'
                  }`}
                >
                  <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'agree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_up</span>
                  {voting ? '...' : 'Agree'}
                </button>
                <button
                  onClick={() => handleVote('disagree')}
                  disabled={voting}
                  aria-label="Disagree"
                  className={`flex items-center justify-center gap-2 py-3 rounded-xl font-bold active:scale-95 transition-all shadow-sm disabled:opacity-60 ${
                    userVote === 'disagree'
                      ? 'bg-error text-white'
                      : 'bg-surface-container-highest text-on-surface hover:bg-error/10 hover:text-error'
                  }`}
                >
                  <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'disagree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_down</span>
                  {voting ? '...' : 'Disagree'}
                </button>
              </div>
            </section>


            {/* Comments Section */}
            <section className="flex flex-col gap-4">
              <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Comments {!commentsLoading && `(${comments.length})`}
              </h3>

              {isAuthenticated ? (
                <div className="flex flex-col gap-2">
                  <textarea
                    className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-xl p-4 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
                    rows={3}
                    placeholder="Add a comment..."
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                  />
                  <button
                    onClick={handleCommentSubmit}
                    disabled={submittingComment || !newComment.trim()}
                    className="self-end px-6 py-2 bg-primary text-on-primary rounded-full text-sm font-bold active:scale-95 transition-all disabled:opacity-50"
                  >
                    {submittingComment ? 'Posting...' : 'Post'}
                  </button>
                </div>
              ) : (
                <p className="text-sm text-on-surface-variant bg-surface-container-lowest p-4 rounded-xl border border-outline-variant/10">
                  <a href="/login" className="font-bold text-primary hover:underline">Log in</a> to leave a comment.
                </p>
              )}

              {commentsLoading ? (
                <p className="text-sm text-on-surface-variant italic">Loading comments...</p>
              ) : comments.length === 0 ? (
                <p className="text-sm text-on-surface-variant italic">No comments yet.</p>
              ) : (
                <div className="flex flex-col gap-4">
                  {comments.map(comment => (
                    <div key={comment.id} className="flex gap-3">
                      <div className="w-8 h-8 rounded-full bg-surface-container-high flex-shrink-0 flex items-center justify-center">
                        <span className="material-symbols-outlined text-sm text-on-surface-variant">person</span>
                      </div>
                      <div className="flex flex-col gap-1 flex-1">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-bold text-on-surface">{comment.author?.name || 'Anonymous'}</p>
                            <p className="text-xs text-outline">{formatDate(comment.createdAt)}</p>
                          </div>
                          {userId && comment.author?.id == userId && (
                            <button
                              onClick={() => handleDeleteComment(comment.id)}
                              className="text-outline hover:text-error transition-colors"
                              aria-label="Delete comment"
                            >
                              <span className="material-symbols-outlined text-sm">delete</span>
                            </button>
                          )}
                        </div>
                        <p className="text-sm text-on-surface-variant leading-relaxed">{comment.content}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
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
                  setFollowing(prev => {
                    const next = !prev
                    onFollowChange?.(next)
                    return next
                  })
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
        </div>
      </aside>
    </div>
  )
}

export default ReportPanel