import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  agreeReport,
  disagreeReport,
  agreeFixRequest,
  disagreeFixRequest,
  mapReport,
  mapFixRequest,
  getCommentsByReport,
  createComment,
  deleteComment,
  followReport,
  unfollowReport,
  getFollowStatus,
} from '../services/reportService.js'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { useUserProfile } from '../hooks/useUserProfile.js'
import { reportKeys, useUpdateMapReport, useDeleteMapReport } from '../hooks/useReports.js'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import CreateFixRequestPanel from './CreateFixRequestPanel.jsx'
import Toast from './Toast.jsx'

const MAX_DESCRIPTION = 1000

/**
 * ReportPanel
 * Desktop: fixed right sidebar (500px) alongside the map.
 * Mobile: bottom sheet (default 60dvh) — pointer-events-none on the outer
 *         wrapper lets the user click the visible map area above. The pill
 *         at the top is draggable to resize between 25/60/90 dvh; dropping
 *         below 15 dvh dismisses via onClose.
 * Props:
 *  - report: mapped report object
 *  - onClose: () => void
 *  - onVoteUpdate: (updatedReport) => void
 */
function ReportPanel({ report, userVote, onVoteChange, onClose, onVoteUpdate, onFollowChange, onShowToast }) {

  const { token, isAuthenticated, userId, isAdmin } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [voteError, setVoteError] = useState('')
  const [fixVoteError, setFixVoteError] = useState('')
  const [comments, setComments] = useState([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [newComment, setNewComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [following, setFollowing] = useState(false)
  const [followLoading, setFollowLoading] = useState(false)
  const [showCreateFix, setShowCreateFix] = useState(false)

  const [isEditing, setIsEditing] = useState(false)
  const [editDescription, setEditDescription] = useState('')
  const [editEnvironment, setEditEnvironment] = useState('OUTDOOR')
  const [editError, setEditError] = useState('')
  const [toast, setToast] = useState(null)
  const updateReportMutation = useUpdateMapReport()
  const deleteReportMutation = useDeleteMapReport()

  // Prefer the parent's toast slot when wired (Home does this), so a toast
  // raised on actions that unmount the panel (e.g. successful delete) still
  // renders. Falls back to local panel-scoped toast for callers that don't
  // pass onShowToast — keeps existing tests untouched.
  const showToast = (t) => {
    if (onShowToast) onShowToast(t)
    else setToast(t)
  }

  const isOwner = !!userId && report?.ownerId != null && String(userId) === String(report.ownerId)
  const { data: reporter } = useUserProfile(report?.ownerId)
  // Admins can edit/delete any report; owners can edit/delete their own.
  const canModify = isOwner || isAdmin

  // Bottom-sheet drag-to-resize (mobile only — desktop layout uses lg: utilities to ignore this).
  // Snap points in dvh; below DISMISS_THRESHOLD on release we call onClose().
  const SHEET_SNAP_POINTS = [25, 60, 90]
  const SHEET_DISMISS_THRESHOLD = 15
  const SHEET_DEFAULT_DVH = 60
  const [sheetHeightDvh, setSheetHeightDvh] = useState(SHEET_DEFAULT_DVH)
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef({ startY: 0, startHeight: SHEET_DEFAULT_DVH, active: false })

  // Inline height has to be conditional — applying it unconditionally would
  // override Tailwind's `lg:h-full` (inline > class). Track viewport via
  // matchMedia so desktop keeps the right-sidebar layout.
  const [isMobileSheet, setIsMobileSheet] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(max-width: 1023px)').matches
      : true
  )
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const mql = window.matchMedia('(max-width: 1023px)')
    function onChange() { setIsMobileSheet(mql.matches) }
    mql.addEventListener?.('change', onChange)
    return () => mql.removeEventListener?.('change', onChange)
  }, [])

  function handleHandlePointerDown(e) {
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = { startY: e.clientY, startHeight: sheetHeightDvh, active: true }
    setIsDragging(true)
  }

  function handleHandlePointerMove(e) {
    if (!dragRef.current.active) return
    const deltaY = e.clientY - dragRef.current.startY
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
        return SHEET_DEFAULT_DVH
      }
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

  useEffect(() => {
    if (!report || !isAuthenticated || !token) return
    getFollowStatus(report.id, token)
      .then(data => setFollowing(data?.following ?? false))
      .catch(() => {})
  }, [report?.id, isAuthenticated])


  

  

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

  const fixVoteMutation = useMutation({
    mutationFn: ({ type }) =>
      type === 'agree'
        ? agreeFixRequest(report.id, report.activeFixRequest.id, token)
        : disagreeFixRequest(report.id, report.activeFixRequest.id, token),
    onSuccess: (updatedFix) => {
      setFixVoteError('')
      // The fix-vote endpoint returns the updated FixRequest, not the full
      // report. Patch activeFixRequest locally for instant feedback. If this
      // vote crossed the threshold, the parent report's status changes too —
      // a follow-up refetch (and the SSE 'fixed' event) brings that home.
      const patched = { ...report, activeFixRequest: mapFixRequest(updatedFix) }
      onVoteUpdate(patched)
      queryClient.invalidateQueries({ queryKey: reportKeys.detail(report.id) })
      queryClient.invalidateQueries({ queryKey: reportKeys.lists() })
    },
    onError: () => {
      setFixVoteError('Failed to vote on fix. Please try again.')
    },
  })

  function handleVote(type) {
    if (!token) { navigate('/login'); return }
    setVoteError('')
    voteMutation.mutate({ type })
  }

  function handleFixVote(type) {
    if (!token) { navigate('/login'); return }
    setFixVoteError('')
    fixVoteMutation.mutate({ type })
  }

  function handleFixSubmitted() {
    // After a successful fix submission, refetch so activeFixRequest appears.
    queryClient.invalidateQueries({ queryKey: reportKeys.detail(report.id) })
    queryClient.invalidateQueries({ queryKey: reportKeys.lists() })
  }

  if (!report) return null

  const isValidated = report.status === 'verified'
  const isFixed = report.status === 'fixed'
  const isRejected = report.status === 'rejected'
  const activeFix = report.activeFixRequest
  const canShowFixCta = isAuthenticated && !isFixed && !activeFix
  const total = (report.agrees || 0) + (report.disagrees || 0)
  const consensusPct = total > 0 ? Math.round(((report.agrees || 0) / total) * 100) : 0
  const voting = voteMutation.isPending
  const fixVoting = fixVoteMutation.isPending

  const fixTotal = activeFix ? (activeFix.agrees || 0) + (activeFix.disagrees || 0) : 0
  const fixPct = fixTotal > 0 ? Math.round(((activeFix.agrees || 0) / fixTotal) * 100) : 0
  const isFixSubmitter = activeFix && userId != null && String(activeFix.submittedByUserId) === String(userId)


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

  function handleStartEdit() {
    setEditDescription(report.description ?? '')
    setEditEnvironment(report.environment ?? 'OUTDOOR')
    setEditError('')
    setIsEditing(true)
  }

  function handleCancelEdit() {
    setIsEditing(false)
    setEditError('')
  }

  async function handleSaveEdit() {
    const trimmed = editDescription.trim()
    if (!trimmed) {
      setEditError('Description cannot be empty.')
      return
    }
    if (trimmed.length > MAX_DESCRIPTION) {
      setEditError(`Description must be at most ${MAX_DESCRIPTION} characters.`)
      return
    }
    setEditError('')
    try {
      await updateReportMutation.mutateAsync({
        id: report.id,
        body: { description: trimmed, environment: editEnvironment },
      })
      setIsEditing(false)
      showToast({ type: 'success', message: 'Report updated.' })
    } catch (e) {
      // Error stays inline in the form (setEditError) AND surfaces a toast
      // so users notice it even if they've scrolled past the form fields.
      setEditError(e.message || 'Failed to save changes.')
      showToast({ type: 'error', message: messageForApiError(e, 'Failed to save changes.') })
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this report? This cannot be undone.')) return
    try {
      await deleteReportMutation.mutateAsync(report.id)
      showToast({ type: 'success', message: 'Report deleted.' })
      // Close panel after the toast is visible — the parent removes the
      // selection state and the SSE event fans the deletion to other
      // clients via useSseSync (REPORT_DELETED).
      onClose()
    } catch (e) {
      showToast({ type: 'error', message: messageForApiError(e, 'Failed to delete report.') })
    }
  }

  // Map common HTTP statuses surfaced by apiFetch into user-friendly text.
  function messageForApiError(err, fallback) {
    const msg = err?.message ?? ''
    if (msg.includes('403')) return 'You don’t have permission to do that.'
    if (msg.includes('404')) return 'Report no longer exists.'
    return msg || fallback
  }

  const editDescriptionTooLong = editDescription.length > MAX_DESCRIPTION
  const saveEditDisabled =
    updateReportMutation.isPending || !editDescription.trim() || editDescriptionTooLong


  return (
    <>
      <div className="fixed inset-0 z-[1200] pointer-events-none flex flex-col justify-end lg:flex-row lg:justify-end">
        <aside
          style={isMobileSheet
            ? {
                height: `${sheetHeightDvh}dvh`,
                maxHeight: `${sheetHeightDvh}dvh`,
                width: '100%',
                borderTopLeftRadius: '32px',
                borderTopRightRadius: '32px',
                borderTop: '1px solid rgba(172,173,173,.2)',
                boxShadow: '0 -10px 40px rgba(0,0,0,0.2)',
              }
            : {
                width: '500px',
                height: '100%',
                maxHeight: '100%',
                borderLeft: '1px solid rgba(172,173,173,.1)',
              }}
          className={`pointer-events-auto bg-surface-container-low flex flex-col relative overflow-y-auto ${isDragging ? '' : 'transition-[height,max-height] duration-200 ease-out'}`}
        >

          {/* Mobile drag handle — pill is decorative, the wider hit-area carries the pointer events. */}
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

        {/* Top status strip — always visible, holds the close button and the
            current status pills so they stay reachable even when an active
            fix card pushes the hero image below the fold. */}
        <div className="flex items-center justify-between px-5 pt-4 pb-2 gap-2">
          <button
            onClick={onClose}
            className="w-8 h-8 bg-white rounded-full flex items-center justify-center shadow border border-outline-variant/20"
            aria-label="Close panel"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
          <div className="flex items-center gap-1.5 flex-wrap justify-end">
            <span className={`text-xs font-bold px-3 py-1.5 rounded-full uppercase tracking-wider ${
              isFixed
                ? 'bg-emerald-100 text-emerald-800'
                : isRejected
                ? 'bg-red-100 text-red-800'
                : isValidated
                ? 'bg-primary-container text-on-primary-container'
                : 'bg-amber-100 text-amber-800'
            }`}>
              {isFixed ? 'Fixed' : isRejected ? 'Rejected' : isValidated ? 'Validated' : 'Unverified'}
            </span>
            {activeFix && (
              <span className="text-[11px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
                Fix pending
              </span>
            )}
          </div>
        </div>

        {/* Active fix request — pinned to the very top of the panel so a
            returning voter sees the live question first, before the original
            report's metadata, image, and description. */}
        {activeFix && (
          <div className="px-5 pb-3">
            <section className="rounded-2xl overflow-hidden ring-1 ring-emerald-200 shadow-sm">
              <div className="px-4 py-2 bg-emerald-700 text-white text-[11px] font-extrabold tracking-widest uppercase flex items-center gap-2">
                <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>build</span>
                Fix Requested · {formatDate(activeFix.createdAt)}
              </div>
              <div className="bg-white p-5 flex flex-col gap-4">
                {activeFix.mediaUrls && activeFix.mediaUrls.length > 0 && (
                  <img
                    src={activeFix.mediaUrls[0]}
                    alt="Proposed fix"
                    className="w-full max-h-56 object-cover rounded-lg"
                  />
                )}
                <div className="flex items-center gap-2 text-xs">
                  <div className="w-7 h-7 rounded-full bg-surface-container-high flex items-center justify-center">
                    <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '14px' }}>person</span>
                  </div>
                  <p>
                    <span className="font-bold text-on-surface">{activeFix.submittedByName || 'Anonymous'}</span>
                    <span className="text-on-surface-variant"> says this is fixed</span>
                  </p>
                </div>
                {activeFix.description && (
                  <p className="text-sm leading-relaxed text-on-surface">{activeFix.description}</p>
                )}

                <div>
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                      Does this look fixed to you?
                    </p>
                    <span className="text-[11px] font-bold text-emerald-700">{fixPct}% consensus</span>
                  </div>
                  <div className="bg-surface-container-high h-1.5 w-full rounded-full overflow-hidden">
                    <div className="h-full rounded-full bg-emerald-600" style={{ width: `${fixPct}%` }} />
                  </div>
                  <p className="text-[11px] text-on-surface-variant mt-2">
                    {activeFix.agrees || 0} agrees · {activeFix.disagrees || 0} disagrees
                  </p>

                  {fixVoteError && (
                    <p role="alert" className="text-xs text-error bg-error-container/20 rounded-lg px-3 py-2 mt-2">
                      {fixVoteError}
                    </p>
                  )}

                  {!isFixSubmitter && (
                    <div className="grid grid-cols-2 gap-2 mt-3">
                      <button
                        onClick={() => handleFixVote('agree')}
                        disabled={fixVoting}
                        className={`py-2.5 rounded-xl text-sm font-bold active:scale-95 transition-all disabled:opacity-60 ${
                          activeFix.userVote === 'agree'
                            ? 'bg-emerald-700 text-white'
                            : 'bg-surface-container-highest text-on-surface hover:bg-emerald-100 hover:text-emerald-800'
                        }`}
                      >
                        {fixVoting ? '…' : 'Yes, fixed'}
                      </button>
                      <button
                        onClick={() => handleFixVote('disagree')}
                        disabled={fixVoting}
                        className={`py-2.5 rounded-xl text-sm font-bold active:scale-95 transition-all disabled:opacity-60 ${
                          activeFix.userVote === 'disagree'
                            ? 'bg-red-700 text-white'
                            : 'bg-surface-container-highest text-on-surface hover:bg-red-100 hover:text-red-800'
                        }`}
                      >
                        {fixVoting ? '…' : 'No, still there'}
                      </button>
                    </div>
                  )}
                  {isFixSubmitter && (
                    <p className="text-[11px] text-on-surface-variant text-center mt-3 italic">
                      You submitted this fix report — the community will vote.
                    </p>
                  )}
                  <p className="text-[11px] text-on-surface-variant text-center mt-3">
                    Confirms as <strong>Fixed</strong> when 5+ agrees AND consensus ≥60%.
                  </p>
                </div>
              </div>
            </section>
          </div>
        )}

        <div className="px-6 pt-2 pb-2">
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
        </div>

        {/* Fix entry CTA — sits between hero and title so a returning visitor
            can flag a resolved obstacle without scrolling. Hidden when the
            report is already FIXED or has an OPEN fix request in flight. */}
        {canShowFixCta && (
          <div className="px-6 pb-2">
            <button
              onClick={() => setShowCreateFix(true)}
              className="w-full flex items-center justify-between gap-3 px-4 py-3 rounded-2xl border border-outline-variant/20 bg-surface-container-lowest hover:bg-emerald-50 transition group"
            >
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-emerald-100 flex items-center justify-center">
                  <span className="material-symbols-outlined text-emerald-700" style={{ fontSize: '20px' }}>build</span>
                </div>
                <div className="text-left">
                  <p className="text-sm font-bold text-on-surface">Has this been fixed?</p>
                  <p className="text-xs text-on-surface-variant">Submit a fix report with a photo</p>
                </div>
              </div>
              <span className="material-symbols-outlined text-on-surface-variant group-hover:translate-x-1 transition" style={{ fontSize: '20px' }}>chevron_right</span>
            </button>
          </div>
        )}

        <div className="px-8 pb-12 flex flex-col gap-8">

          {/* Header */}
          <div className="flex flex-col gap-4">
            <div className="flex items-start justify-between gap-3">
              <h1 className="text-3xl font-extrabold font-headline tracking-tight text-on-surface leading-tight">
                {report.title}
              </h1>
              {canModify && !isEditing && (
                <div className="flex items-center gap-2 flex-shrink-0">
                  <button
                    type="button"
                    onClick={handleStartEdit}
                    className="px-3 py-1.5 rounded-lg bg-surface-container-high text-on-surface font-semibold text-sm cursor-pointer"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={deleteReportMutation.isPending}
                    aria-label="Delete report"
                    className="px-3 py-1.5 rounded-lg bg-error/10 text-error font-semibold text-sm cursor-pointer disabled:opacity-60"
                  >
                    {deleteReportMutation.isPending ? 'Deleting…' : 'Delete'}
                  </button>
                </div>
              )}
            </div>
            <div className="flex items-center justify-between">
              <button
                type="button"
                onClick={() => report?.ownerId && navigate(isOwner ? '/profile' : `/profile/${report.ownerId}`)}
                className="flex items-start gap-3 cursor-pointer group"
              >
                <div className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center overflow-hidden flex-shrink-0">
                  {reporter?.avatarUrl ? (
                    <img src={reporter.avatarUrl} alt={reporter.name} className="w-full h-full object-cover" />
                  ) : (
                    <span className="material-symbols-outlined text-on-surface-variant">person</span>
                  )}
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-on-surface group-hover:underline">
                    {reporter?.name ?? report.reportedBy}
                  </p>
                  {report.date && (
                    <p className="text-xs text-on-surface-variant">{report.date}</p>
                  )}
                </div>
              </button>
              {report.environment && (
                <span className="flex items-center gap-1 text-xs font-semibold text-on-surface-variant bg-surface-container px-2.5 py-1 rounded-lg">
                  <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>
                    {report.environment === 'INDOOR' ? 'home' : 'wb_sunny'}
                  </span>
                  {report.environment === 'INDOOR' ? 'Indoor' : 'Outdoor'}
                </span>
              )}
            </div>
          </div>

          {/* Objects */}
          {report.objects?.length > 0 && (
            <section className="flex flex-col gap-3">
              <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">Objects</h3>
              {report.objects.map((obj, i) => {
                const cfg = OBJECT_TYPE_MAP[obj.objectType]
                return (
                  <div key={i} className="bg-surface-container-lowest rounded-xl border border-outline-variant/10 p-4 flex flex-col gap-3">
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
                        <span className="material-symbols-outlined text-primary" style={{ fontSize: '18px', fontVariationSettings: "'FILL' 1" }}>
                          {cfg?.icon ?? 'category'}
                        </span>
                      </div>
                      <span className="text-sm font-bold text-on-surface">{cfg?.label ?? obj.objectType}</span>
                    </div>

                    {obj.issues?.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {obj.issues.map(issueKey => (
                          <span key={issueKey} className="flex items-center gap-1 text-[11px] font-semibold px-2.5 py-1 rounded-full bg-error/10 text-error border border-error/15">
                            <span className="material-symbols-outlined" style={{ fontSize: '12px' }}>warning</span>
                            {cfg?.issues.find(i => i.key === issueKey)?.label ?? issueKey.replace(/_/g, ' ')}
                          </span>
                        ))}
                      </div>
                    )}

                    {obj.measurements && Object.keys(obj.measurements).length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {Object.entries(obj.measurements).map(([key, val]) => {
                          const schema = cfg?.measurements.find(m => m.key === key)
                          const numVal = parseFloat(val)
                          const isWarn = schema && (
                            (schema.accessible_max !== undefined && numVal > schema.accessible_max) ||
                            (schema.accessible_min !== undefined && numVal < schema.accessible_min)
                          )
                          return (
                            <span key={key} className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg flex items-center gap-1 ${
                              isWarn
                                ? 'bg-error/10 text-error border border-error/15'
                                : 'bg-primary/10 text-primary border border-primary/15'
                            }`}>
                              <span className="material-symbols-outlined" style={{ fontSize: '12px' }}>
                                {isWarn ? 'close' : 'check'}
                              </span>
                              {schema?.label ?? key}: {val} {schema?.unit ?? ''}
                              {schema && (schema.accessible_min !== undefined || schema.accessible_max !== undefined) && (
                                <span className="opacity-60 font-normal ml-0.5">
                                  ({schema.accessible_min !== undefined ? `≥${schema.accessible_min}` : `≤${schema.accessible_max}`} ok)
                                </span>
                              )}
                            </span>
                          )
                        })}
                      </div>
                    )}
                  </div>
                )
              })}
            </section>
          )}

          {/* Description */}
          <section className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10">
            <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              Issue Details
            </h3>
            {!isEditing ? (
              <p className="text-on-surface leading-relaxed font-body">
                {report.description || 'No description provided.'}
              </p>
            ) : (
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-1">
                  <label htmlFor="report-edit-description" className="text-sm font-semibold text-on-surface">
                    Description
                  </label>
                  <textarea
                    id="report-edit-description"
                    value={editDescription}
                    onChange={(e) => setEditDescription(e.target.value)}
                    rows={5}
                    className="w-full px-4 py-3 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40"
                  />
                  <p className={`text-xs text-right ${editDescriptionTooLong ? 'text-error' : 'text-on-surface-variant'}`}>
                    {editDescription.length}/{MAX_DESCRIPTION}
                  </p>
                </div>

                <fieldset className="flex flex-col gap-1">
                  <legend className="text-sm font-semibold text-on-surface">Environment</legend>
                  <div className="flex gap-3 mt-1">
                    {['OUTDOOR', 'INDOOR'].map((opt) => (
                      <label
                        key={opt}
                        className={`flex items-center gap-2 px-3 py-2 rounded-xl cursor-pointer ${editEnvironment === opt ? 'bg-primary/10 ring-2 ring-primary' : 'bg-[#f0f1f1]'}`}
                      >
                        <input
                          type="radio"
                          name="report-edit-environment"
                          value={opt}
                          checked={editEnvironment === opt}
                          onChange={() => setEditEnvironment(opt)}
                        />
                        <span className="text-sm text-on-surface capitalize">{opt.toLowerCase()}</span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                {editError && (
                  <p role="alert" className="text-sm text-error">
                    {editError}
                  </p>
                )}

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleSaveEdit}
                    disabled={saveEditDisabled}
                    className="px-4 py-2 rounded-lg bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-semibold disabled:opacity-60 cursor-pointer"
                  >
                    {updateReportMutation.isPending ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    type="button"
                    onClick={handleCancelEdit}
                    disabled={updateReportMutation.isPending}
                    className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
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
              disabled={followLoading}
              onClick={async () => {
                if (!isAuthenticated) { navigate('/login'); return }
                setFollowLoading(true)
                try {
                  if (following) {
                    await unfollowReport(report.id, token)
                    setFollowing(false)
                    onFollowChange?.(false)
                  } else {
                    await followReport(report.id, token)
                    setFollowing(true)
                    onFollowChange?.(true)
                  }
                } catch (err) {
                  console.error('[ReportPanel] Follow toggle failed:', err)
                } finally {
                  setFollowLoading(false)
                }
              }}
              className={`w-full py-5 rounded-xl font-extrabold text-lg font-headline shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-60 disabled:cursor-not-allowed ${
                following
                  ? 'bg-surface-container-high text-on-surface border border-outline-variant/20'
                  : 'bg-gradient-to-b from-primary to-primary-dim text-on-primary'
              }`}
            >
              {followLoading
                ? <span className="material-symbols-outlined animate-spin">progress_activity</span>
                : <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>
                    {following ? 'notifications_off' : 'notifications_active'}
                  </span>
              }
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
      </div>

      {showCreateFix && (
        <CreateFixRequestPanel
          reportId={report.id}
          reportTitle={report.title}
          onClose={() => setShowCreateFix(false)}
          onSubmitted={handleFixSubmitted}
        />
      )}

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onDismiss={() => setToast(null)}
        />
      )}
    </>
  )
}

export default ReportPanel
