import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

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
import { OBJECT_TYPE_MAP, localizeObjectType } from '../utils/objectTypeConfig.js'
import { reportJsonLdString } from '../utils/schemaOrg.js'
import CreateFixRequestPanel from './CreateFixRequestPanel.jsx'
import Toast from './Toast.jsx'
import BadgeIcon from './BadgeIcon.jsx'

const MAX_DESCRIPTION = 1000

// S3 returns plain URLs ending in the original filename, so the extension is
// the cheapest way to know whether a media URL points at a video. Backend's
// allowlist (S3MediaService.ALLOWED_CONTENT_TYPES) is mp4 + quicktime today.
const VIDEO_EXTENSIONS = ['.mp4', '.mov', '.webm']
function isVideoUrl(url) {
  if (!url) return false
  const path = url.toLowerCase().split('?')[0]
  return VIDEO_EXTENSIONS.some((ext) => path.endsWith(ext))
}

// Centered fullscreen viewer for a single media URL or a list. Closes on
// Escape, backdrop click, or the X button. ArrowLeft/ArrowRight navigate
// when there is more than one item. Portaled to <body> so the panel's
// pointer-events-none wrapper doesn't swallow clicks.
function MediaLightbox({ items, startIndex = 0, title, onClose }) {
  const { t } = useTranslation()
  const [index, setIndex] = useState(startIndex)

  useEffect(() => {
    function handleKey(e) {
      if (e.key === 'Escape') onClose()
      else if (e.key === 'ArrowLeft' && items.length > 1) {
        setIndex((i) => (i === 0 ? items.length - 1 : i - 1))
      } else if (e.key === 'ArrowRight' && items.length > 1) {
        setIndex((i) => (i === items.length - 1 ? 0 : i + 1))
      }
    }
    document.addEventListener('keydown', handleKey)
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', handleKey)
      document.body.style.overflow = prevOverflow
    }
  }, [items.length, onClose])

  if (!items || items.length === 0) return null
  if (typeof document === 'undefined') return null

  const current = items[index]
  const isVideo = isVideoUrl(current)

  function handleBackdrop(e) {
    if (e.target === e.currentTarget) onClose()
  }

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title ? `${title} media viewer` : 'Media viewer'}
      onClick={handleBackdrop}
      className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/85 backdrop-blur-sm p-4"
    >
      <button
        type="button"
        onClick={onClose}
        aria-label="Close viewer"
        className="absolute top-4 right-4 w-11 h-11 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition cursor-pointer"
      >
        <span className="material-symbols-outlined">close</span>
      </button>

      {items.length > 1 && (
        <div className="absolute top-5 left-1/2 -translate-x-1/2 px-3 py-1.5 rounded-full bg-white/10 text-white text-xs font-semibold tracking-wide">
          {index + 1} / {items.length}
        </div>
      )}

      {items.length > 1 && (
        <>
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); setIndex((i) => (i === 0 ? items.length - 1 : i - 1)) }}
            aria-label={t('report.previousImage')}
            className="absolute left-4 top-1/2 -translate-y-1/2 w-12 h-12 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition cursor-pointer"
          >
            <span className="material-symbols-outlined text-2xl">chevron_left</span>
          </button>
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); setIndex((i) => (i === items.length - 1 ? 0 : i + 1)) }}
            aria-label={t('report.nextImage')}
            className="absolute right-4 top-1/2 -translate-y-1/2 w-12 h-12 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition cursor-pointer"
          >
            <span className="material-symbols-outlined text-2xl">chevron_right</span>
          </button>
        </>
      )}

      <div
        className="max-w-[90vw] max-h-[85vh] flex items-center justify-center"
        onClick={(e) => e.stopPropagation()}
      >
        {isVideo ? (
          <video
            key={current}
            src={current}
            controls
            autoPlay
            playsInline
            className="max-w-[90vw] max-h-[85vh] rounded-lg bg-black"
          />
        ) : (
          <img
            src={current}
            alt={title ? `${title} - Photo ${index + 1}` : `Photo ${index + 1}`}
            className="max-w-[90vw] max-h-[85vh] object-contain rounded-lg"
          />
        )}
      </div>
    </div>,
    document.body,
  )
}

// Unified slider for any mix of images and videos. Each slide renders an
// <img> or <video> based on the URL — a video in any slot no longer blocks
// navigation to the next item (the previous implementation rendered a bare
// <video> and dropped the carousel entirely when slot 0 was a video).
// Click an image (or the expand button on a video) to open MediaLightbox.
function MediaCarousel({ items, title, heightClass = 'h-64' }) {
  const { t } = useTranslation()
  const [currentIndex, setCurrentIndex] = useState(0)
  const [lightboxOpen, setLightboxOpen] = useState(false)

  if (!items || items.length === 0) {
    return (
      <div className={`w-full ${heightClass} bg-surface-container rounded-xl shadow-sm flex items-center justify-center border border-outline-variant/10`}>
        <span className="material-symbols-outlined text-6xl text-outline-variant">image</span>
      </div>
    )
  }

  const safeIndex = Math.min(currentIndex, items.length - 1)
  const current = items[safeIndex]
  const isVideo = isVideoUrl(current)

  const handlePrev = (e) => {
    e.stopPropagation()
    setCurrentIndex((prev) => (prev === 0 ? items.length - 1 : prev - 1))
  }

  const handleNext = (e) => {
    e.stopPropagation()
    setCurrentIndex((prev) => (prev === items.length - 1 ? 0 : prev + 1))
  }

  return (
    <>
      <div className={`relative w-full ${heightClass} rounded-xl shadow-sm border border-outline-variant/10 overflow-hidden group bg-black/5`}>
        {isVideo ? (
          <>
            {/* keyed by URL so React tears down the previous <video> when
              the user navigates between slides — otherwise the element
              keeps the old src queued and playback flickers. */}
            <video
              key={current}
              src={current}
              controls
              playsInline
              className="w-full h-full object-cover bg-black"
            />
            {/* Native video controls live at the bottom edge, so the
              expand affordance sits top-right out of the way. */}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); setLightboxOpen(true) }}
              aria-label={t('report.openInViewer')}
              className="absolute top-2 right-2 w-9 h-9 rounded-full bg-black/50 hover:bg-black/70 text-white flex items-center justify-center backdrop-blur-sm cursor-pointer z-10"
            >
              <span className="material-symbols-outlined text-lg">open_in_full</span>
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={() => setLightboxOpen(true)}
            aria-label={t('report.openInViewer')}
            className="block w-full h-full cursor-zoom-in"
          >
            <img
              src={current}
              alt={`${title} - ${t('report.photo')} ${safeIndex + 1}`}
              className="w-full h-full object-cover transition-opacity duration-300"
            />
          </button>
        )}

        {items.length > 1 && (
          <>
            {/* Always visible — the previous hover-only treatment hid the
              arrows on touch and was easy to miss with a mouse too. */}
            <button
              onClick={handlePrev}
              className="absolute left-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/55 hover:bg-black/75 text-white flex items-center justify-center backdrop-blur-sm transition-colors z-10 shadow-md"
              aria-label={t('report.previousImage')}
            >
              <span className="material-symbols-outlined text-xl">chevron_left</span>
            </button>

            <button
              onClick={handleNext}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/55 hover:bg-black/75 text-white flex items-center justify-center backdrop-blur-sm transition-colors z-10 shadow-md"
              aria-label={t('report.nextImage')}
            >
              <span className="material-symbols-outlined text-xl">chevron_right</span>
            </button>

            <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-1.5 px-2 py-1 rounded-full bg-black/20 backdrop-blur-sm z-10">
              {items.map((_, i) => (
                <button
                  key={i}
                  onClick={(e) => { e.stopPropagation(); setCurrentIndex(i) }}
                  className={`w-1.5 h-1.5 rounded-full transition-all ${safeIndex === i ? 'bg-white w-3' : 'bg-white/50 hover:bg-white/80'}`}
                  aria-label={t('report.goToMedia', { index: i + 1 })}
                />
              ))}
            </div>
          </>
        )}
      </div>

      {lightboxOpen && (
        <MediaLightbox
          items={items}
          startIndex={safeIndex}
          title={title}
          onClose={() => setLightboxOpen(false)}
        />
      )}
    </>
  )
}

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
  const { t } = useTranslation()
  const { token, isAuthenticated, userId, isAdmin } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const fromFeed = searchParams.get('from') === 'feed'
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
      .catch(() => { })
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
      setVoteError(t('report.voteFailed'))
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
      setFixVoteError(t('report.fixVoteFailed'))
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

  // Rebuild the report title at render time so the object labels follow the
  // active language. mapReport() pre-builds an English title; we override it
  // here using translated labels from OBJECT_TYPE_MAP.
  const displayTitle = (() => {
    if (!report.objects?.length) {
      // Tests and legacy fixtures pass a pre-built `title`; honour it when
      // there's no object list to derive from.
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
      setEditError(t('report.descriptionEmpty'))
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
      showToast({ type: 'success', message: t('report.updateSuccess') })
    } catch (e) {
      // Error stays inline in the form (setEditError) AND surfaces a toast
      // so users notice it even if they've scrolled past the form fields.
      setEditError(e.message || t('report.updateFailed'))
      showToast({ type: 'error', message: messageForApiError(e, t('report.updateFailed')) })
    }
  }

  async function handleDelete() {
    if (!window.confirm(t('report.deleteConfirm'))) return
    try {
      await deleteReportMutation.mutateAsync(report.id)
      showToast({ type: 'success', message: t('report.deleteSuccess') })
      // Close panel after the toast is visible — the parent removes the
      // selection state and the SSE event fans the deletion to other
      // clients via useSseSync (REPORT_DELETED).
      onClose()
    } catch (e) {
      showToast({ type: 'error', message: messageForApiError(e, t('report.deleteFailed')) })
    }
  }

  // Map common HTTP statuses surfaced by apiFetch into user-friendly text.
  function messageForApiError(err, fallback) {
    const msg = err?.message ?? ''
    if (msg.includes('403')) return t('report.noPermission')
    if (msg.includes('404')) return t('report.reportGone')
    return msg || fallback
  }

  const editDescriptionTooLong = editDescription.length > MAX_DESCRIPTION
  const saveEditDisabled =
    updateReportMutation.isPending || !editDescription.trim() || editDescriptionTooLong

  // Schema.org JSON-LD describing this report as a Place — lets search engines
  // and assistive tools surface its accessibility metadata. The url field
  // mirrors the panel's deep-link (`/?report=<id>`) so the canonical resource
  // matches what a crawler would land on.
  const jsonLdString = reportJsonLdString(report, {
    url: typeof window !== 'undefined'
      ? `${window.location.origin}/?report=${report.id}`
      : undefined,
  })


  return (
    <>
      {jsonLdString && (
        <script
          type="application/ld+json"
          data-testid="report-jsonld"
          // eslint-disable-next-line react/no-danger
          dangerouslySetInnerHTML={{ __html: jsonLdString }}
        />
      )}
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
            aria-label={t('report.resizePanel')}
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
            <div className="flex items-center gap-2 min-w-0">
              {fromFeed && (
                <button
                  type="button"
                  onClick={() => navigate('/feed')}
                  className="shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold uppercase tracking-wide bg-primary/10 text-primary border border-primary/30 hover:bg-primary/15 transition-colors"
                >
                  <span className="material-symbols-outlined text-[16px]">arrow_back</span>
                  {t('report.backToFeed')}
                </button>
              )}
              <button
                onClick={onClose}
                className="w-8 h-8 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors shrink-0"
                aria-label={t('report.closePanel')}
              >
                <span className="material-symbols-outlined text-base">close</span>
              </button>
            </div>
            <div className="flex items-center gap-1.5 flex-wrap justify-end">
              <span className={`text-xs font-bold px-3 py-1.5 rounded-full uppercase tracking-wider ${isFixed
                ? 'bg-emerald-100 text-emerald-800'
                : isRejected
                  ? 'bg-red-100 text-red-800'
                  : isValidated
                    ? 'bg-primary-container text-on-primary-container'
                    : 'bg-amber-100 text-amber-800'
                }`}>
                {isFixed ? t('report.statusFixed') : isRejected ? t('report.statusRejected') : isValidated ? t('report.statusValidated') : t('report.statusUnverified')}
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
                    <MediaCarousel
                      items={activeFix.mediaUrls}
                      title="Proposed fix"
                      heightClass="h-56"
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
                      <span className="text-[11px] font-bold text-emerald-700">{t('report.fixConsensus', { percent: fixPct })}</span>
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
                          className={`py-2.5 rounded-xl text-sm font-bold active:scale-95 transition-all disabled:opacity-60 ${activeFix.userVote === 'agree'
                            ? 'bg-emerald-700 text-white'
                            : 'bg-surface-container-highest text-on-surface hover:bg-emerald-100 hover:text-emerald-800'
                            }`}
                        >
                          {fixVoting ? '…' : t('report.yesFixed')}
                        </button>
                        <button
                          onClick={() => handleFixVote('disagree')}
                          disabled={fixVoting}
                          className={`py-2.5 rounded-xl text-sm font-bold active:scale-95 transition-all disabled:opacity-60 ${activeFix.userVote === 'disagree'
                            ? 'bg-red-700 text-white'
                            : 'bg-surface-container-highest text-on-surface hover:bg-red-100 hover:text-red-800'
                            }`}
                        >
                          {fixVoting ? '…' : t('report.noStillThere')}
                        </button>
                      </div>
                    )}
                    {isFixSubmitter && (
                      <p className="text-[11px] text-on-surface-variant text-center mt-3 italic">
                        You submitted this fix report — the community will vote.
                      </p>
                    )}
                    <p className="text-[11px] text-on-surface-variant text-center mt-3">
                      {t('report.fixedConsensusNote')}
                    </p>
                  </div>
                </div>
              </section>
            </div>
          )}

          <div className="px-6 pt-2 pb-2">
            {(() => {
              const allMedia = report.media?.length > 0
                ? report.media.map(m => m.url || m)
                : (report.mediaUrls?.length > 0 ? report.mediaUrls : (report.image ? [report.image] : []))
              return <MediaCarousel items={allMedia} title={displayTitle} />
            })()}
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
                    <p className="text-sm font-bold text-on-surface">{t('report.hasThisBeenFixed')}</p>
                    <p className="text-xs text-on-surface-variant">{t('report.submitFixWithPhoto')}</p>
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
                  {displayTitle}
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
                      aria-label={t('report.deleteReport')}
                      className="px-3 py-1.5 rounded-lg bg-error/10 text-error font-semibold text-sm cursor-pointer disabled:opacity-60"
                    >
                      {deleteReportMutation.isPending ? t('report.deleting') : t('report.delete')}
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
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <p className="text-sm font-bold text-on-surface group-hover:underline">
                        {reporter?.name ?? report.reportedBy}
                      </p>
                      {report.authorTopBadge && (
                        <BadgeIcon badge={report.authorTopBadge} size="sm" />
                      )}
                    </div>
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
                    {report.environment === 'INDOOR' ? t('report.indoor') : t('report.outdoor')}
                  </span>
                )}
              </div>
            </div>

            {/* Objects */}
            {report.objects?.length > 0 && (
              <section className="flex flex-col gap-3">
                <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">{t('report.objects')}</h3>
                {report.objects.map((obj, i) => {
                  const cfg = localizeObjectType(t, OBJECT_TYPE_MAP[obj.objectType])
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
                              <span key={key} className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg flex items-center gap-1 ${isWarn
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
                {t('report.issueDetails')}
              </h3>
              {!isEditing ? (
                <p className="text-on-surface leading-relaxed font-body">
                  {report.description || t('report.noDescription')}
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
                      {updateReportMutation.isPending ? t('report.saving') : t('report.save')}
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
                  {t('report.communityConsensus')}
                </h3>
                <span className="text-xs font-bold text-primary">{t('report.consensusPercent', { percent: consensusPct })}</span>
              </div>
              <div className="bg-surface-container-high h-2.5 w-full rounded-full overflow-hidden">
                <div
                  className="bg-gradient-to-r from-primary-container to-primary h-full rounded-full transition-all"
                  style={{ width: `${consensusPct}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-sm text-on-surface-variant italic">
                <span>{t('report.peopleAgreed', { count: report.agrees || 0 })}</span>
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
                  aria-label={t('report.agree')}
                  className={`flex items-center justify-center gap-2 py-3 rounded-xl font-bold active:scale-95 transition-all shadow-sm disabled:opacity-60 ${userVote === 'agree'
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-container-highest text-on-surface hover:bg-primary/10 hover:text-primary'
                    }`}
                >
                  <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'agree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_up</span>
                  {voting ? '...' : t('report.agree')}
                </button>
                <button
                  onClick={() => handleVote('disagree')}
                  disabled={voting}
                  aria-label={t('report.disagree')}
                  className={`flex items-center justify-center gap-2 py-3 rounded-xl font-bold active:scale-95 transition-all shadow-sm disabled:opacity-60 ${userVote === 'disagree'
                    ? 'bg-error text-white'
                    : 'bg-surface-container-highest text-on-surface hover:bg-error/10 hover:text-error'
                    }`}
                >
                  <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: userVote === 'disagree' ? "'FILL' 1" : "'FILL' 0" }}>thumb_down</span>
                  {voting ? '...' : t('report.disagree')}
                </button>
              </div>
            </section>


            {/* Comments Section */}
            <section className="flex flex-col gap-4">
              <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                {t('report.comments')} {!commentsLoading && `(${comments.length})`}
              </h3>

              {isAuthenticated ? (
                <div className="flex flex-col gap-2">
                  <textarea
                    className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-xl p-4 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
                    rows={3}
                    placeholder={t('report.addComment')}
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                  />
                  <button
                    onClick={handleCommentSubmit}
                    disabled={submittingComment || !newComment.trim()}
                    className="self-end px-6 py-2 bg-primary text-on-primary rounded-full text-sm font-bold active:scale-95 transition-all disabled:opacity-50"
                  >
                    {submittingComment ? t('report.posting') : t('report.post')}
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
                <p className="text-sm text-on-surface-variant italic">{t('report.noCommentsYet')}</p>
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
                              aria-label={t('report.deleteComment')}
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
                {t('report.recentActivity')}
              </h3>
              <div className="relative pl-6 flex flex-col gap-8">
                <div className="absolute left-[7px] top-2 bottom-2 w-[2px] bg-surface-container-highest" />
                <div className="relative">
                  <div className="absolute -left-[23px] top-1 w-4 h-4 rounded-full bg-primary ring-4 ring-surface-container-low" />
                  <div className="flex flex-col gap-1">
                    <p className="text-sm font-bold text-on-surface">{t('report.system')}</p>
                    <p className="text-sm text-on-surface-variant">
                      {t('report.submittedActivity')}
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
                className={`w-full py-5 rounded-xl font-extrabold text-lg font-headline shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-60 disabled:cursor-not-allowed ${following
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
                {following ? t('report.unfollow') : t('report.follow')}
              </button>
              <p className="text-center text-xs text-on-surface-variant mt-4 px-6">
                {following
                  ? t('report.followCopyFollowing')
                  : t('report.followCopyNotFollowing')}
              </p>
            </div>

          </div>
        </aside>
      </div>

      {showCreateFix && (
        <CreateFixRequestPanel
          reportId={report.id}
          reportTitle={displayTitle}
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
