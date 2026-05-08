import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

import { agreeReport, disagreeReport, mapReport, getCommentsByReport, createComment, deleteComment } from '../services/reportService.js'
import { useMutation } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { useUpdateMapReport } from '../hooks/useReports.js'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'

const MAX_DESCRIPTION = 1000

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
  const [voteError, setVoteError] = useState('')
  const [comments, setComments] = useState([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [newComment, setNewComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [following, setFollowing] = useState(false)

  const [isEditing, setIsEditing] = useState(false)
  const [editDescription, setEditDescription] = useState('')
  const [editEnvironment, setEditEnvironment] = useState('OUTDOOR')
  const [editError, setEditError] = useState('')
  const updateReportMutation = useUpdateMapReport()

  const isOwner = !!userId && report?.ownerId != null && String(userId) === String(report.ownerId)

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
    } catch (e) {
      setEditError(e.message || 'Failed to save changes.')
    }
  }

  const editDescriptionTooLong = editDescription.length > MAX_DESCRIPTION
  const saveEditDisabled =
    updateReportMutation.isPending || !editDescription.trim() || editDescriptionTooLong


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
              className="absolute top-4 left-4 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center shadow"
              aria-label="Close panel"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          </div>
        </div>

        <div className="px-8 pb-12 flex flex-col gap-8">

          {/* Header */}
          <div className="flex flex-col gap-4">
            <div className="flex items-start justify-between gap-3">
              <h1 className="text-3xl font-extrabold font-headline tracking-tight text-on-surface leading-tight">
                {report.title}
              </h1>
              {isOwner && !isEditing && (
                <button
                  type="button"
                  onClick={handleStartEdit}
                  className="px-3 py-1.5 rounded-lg bg-surface-container-high text-on-surface font-semibold text-sm cursor-pointer flex-shrink-0"
                >
                  Edit
                </button>
              )}
            </div>
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
    </>
  )
}

export default ReportPanel
