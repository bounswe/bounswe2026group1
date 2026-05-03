import { useState, useEffect } from 'react'
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
} from '../services/reportService.js'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'
import { reportKeys } from '../hooks/useReports.js'
import CreateFixRequestPanel from './CreateFixRequestPanel.jsx'

/**
 * ReportPanel
 * Desktop: fixed right sidebar (500px) alongside the map.
 * Mobile: full screen overlay.
 * Props:
 *  - report: mapped report object
 *  - onClose: () => void
 *  - onVoteUpdate: (updatedReport) => void
 */
function ReportPanel({ report, userVote, onVoteChange, onClose, onVoteUpdate, onFollowChange }) {

  const { token, isAuthenticated, userId } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [voteError, setVoteError] = useState('')
  const [fixVoteError, setFixVoteError] = useState('')
  const [comments, setComments] = useState([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [newComment, setNewComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [following, setFollowing] = useState(false)
  const [showCreateFix, setShowCreateFix] = useState(false)

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


  return (
    <>
      <div
        className="fixed inset-0 bg-black/20 z-[1100] lg:hidden"
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className="
        fixed top-0 right-0 h-full z-[1200]
        w-full lg:w-[500px]
        bg-surface-container-low
        overflow-y-auto
        border-l border-outline-variant/10
        flex flex-col
      ">

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

            <div className="absolute top-4 right-4 flex flex-col items-end gap-1.5">
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

            <button
              onClick={onClose}
              className="absolute top-4 left-4 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center shadow"
              aria-label="Close panel"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          </div>
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

          {/* Active fix request — the live community vote on whether this
              obstacle has been resolved. Sits above the original report
              details so a returning voter sees the active question first. */}
          {activeFix && (
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
          )}

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
      </aside>

      {showCreateFix && (
        <CreateFixRequestPanel
          reportId={report.id}
          reportTitle={report.title}
          onClose={() => setShowCreateFix(false)}
          onSubmitted={handleFixSubmitted}
        />
      )}
    </>
  )
}

export default ReportPanel
