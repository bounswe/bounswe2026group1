import { apiFetch } from './api.js'

/**
 * Fetch all reports from the backend.
 * GET /api/reports
 */
export async function getReports() {
  return apiFetch('/api/reports')
}

/**
 * Fetch a single report by ID.
 * GET /api/reports/{id}
 */
export async function getReportById(id) {
  return apiFetch(`/api/reports/${id}`)
}

/**
 * Submit an agree vote on a report.
 */
export async function agreeReport(id, token) {
  return apiFetch(`/api/reports/${id}/agree`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Submit a disagree vote on a report.
 */
export async function disagreeReport(id, token) {
  return apiFetch(`/api/reports/${id}/disagree`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Fetch comments for a specific report.
 * GET /api/comments/report/{reportId}
 */
export async function getCommentsByReport(reportId) {
  return apiFetch(`/api/comments/report/${reportId}`)
}

/**
 * Post a new comment on a report.
 * POST /api/comments
 */
// reportService.js
export async function createComment(reportId, content, token) {
  console.log('[createComment] Sending comment', { reportId, content, token })

  try {
    // Backend usually expects reportId at top-level, not nested in 'report'
    const payload = { content, reportId }

    const result = await apiFetch('/api/comments', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify(payload),
    })

    console.log('[createComment] Success:', result)
    return result
  } catch (err) {
    console.error('[createComment] Failed', err)
    throw err
  }
}

/**
 * Map API ReportResponse fields to ReportPanel prop shape.
 */
export function mapReport(r) {
  const tagLabels = {
    MISSING_RAMP: 'Missing Ramp',
    BROKEN_ELEVATOR: 'Broken Elevator',
    NARROW_PASSAGE: 'Narrow Passage',
    WET_FLOOR: 'Wet Floor',
    CONSTRUCTION: 'Construction',
    OTHER: 'Other',
  }

  return {
    id: r.reportId,
    title: tagLabels[r.tag] || r.tag,
    description: r.description,
    status: r.status === 'VERIFIED' ? 'verified' : 'unverified',
    date: r.publishDate
      ? new Date(r.publishDate).toLocaleDateString('en-GB', {
          day: 'numeric', month: 'long', year: 'numeric',
        })
      : 'Unknown date',
    location: `${r.latitude.toFixed(4)}, ${r.longitude.toFixed(4)}`,
    reportedBy: `User #${r.userId}`,
    agrees: r.agrees,
    disagrees: r.disagrees,
    tags: [tagLabels[r.tag] || r.tag],
    image: r.mediaUrls && r.mediaUrls.length > 0 ? r.mediaUrls[0] : null,
    latitude: r.latitude,
    longitude: r.longitude,
  }
}


/**
 * Delete a comment by ID.
 * DELETE /api/comments/{id}
 */
export async function deleteComment(commentId, token) {
  return apiFetch(`/api/comments/${commentId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}