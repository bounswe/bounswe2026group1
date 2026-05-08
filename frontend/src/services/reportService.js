import { apiFetch } from './api.js'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'

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
  return apiFetch(`/api/reports/${id}/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Submit a disagree vote on a report.
 */
export async function disagreeReport(id, token) {
  return apiFetch(`/api/reports/${id}/unverify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Submit a fix request on a report.
 * POST /api/reports/{reportId}/fix-requests   (multipart)
 *
 * Uses fetch directly because apiFetch sets a JSON Content-Type which would
 * break the multipart boundary that FormData generates automatically.
 */
export async function submitFixRequest(reportId, file, description, token) {
  const formData = new FormData()
  formData.append('files', file)
  if (description && description.trim()) {
    formData.append('description', description.trim())
  }

  const res = await fetch(
    `${import.meta.env.VITE_API_URL}/api/reports/${reportId}/fix-requests`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Mapcess-Key': import.meta.env.VITE_API_KEY,
      },
      body: formData,
    }
  )

  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new CustomEvent('auth:expired'))
    const error = await res.json().catch(() => ({ message: res.statusText }))
    const err = new Error(error.message || res.statusText)
    err.status = res.status
    throw err
  }

  return res.json()
}

/**
 * Submit an agree vote on a fix request.
 * POST /api/reports/{reportId}/fix-requests/{fixId}/agree
 */
export async function agreeFixRequest(reportId, fixId, token) {
  return apiFetch(`/api/reports/${reportId}/fix-requests/${fixId}/agree`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Submit a disagree vote on a fix request.
 * POST /api/reports/{reportId}/fix-requests/{fixId}/disagree
 */
export async function disagreeFixRequest(reportId, fixId, token) {
  return apiFetch(`/api/reports/${reportId}/fix-requests/${fixId}/disagree`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Fetch comments for a specific report.
 * GET /api/comments/report/{reportId}
 */
export async function getCommentsByReport(reportId, token) {
  return apiFetch(`/api/comments/report/${reportId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

/**
 * Post a new comment on a report.
 * POST /api/comments
 */
export async function createComment(reportId, content, token, userId) {
  const payload = {
    content,
    author: { id: userId },
    report: { reportId },
  }

  return apiFetch('/api/comments', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  })
}

/**
 * Map an API FixRequestResponse to the shape consumed by ReportPanel's
 * active-fix card.
 */
export function mapFixRequest(fr) {
  if (!fr) return null
  return {
    id: fr.id,
    reportId: fr.reportId,
    submittedByUserId: fr.submittedByUserId,
    submittedByName: fr.submittedByName,
    description: fr.description,
    state: fr.state,
    agrees: fr.agrees,
    disagrees: fr.disagrees,
    createdAt: fr.createdAt,
    resolvedAt: fr.resolvedAt,
    mediaUrls: fr.mediaUrls || [],
    userVote: fr.userVote ? fr.userVote.toLowerCase() : null,
  }
}

/**
 * Map an API ReportStatus enum value to the lowercase tag the panel expects.
 * Anything other than VERIFIED / FIXED / REJECTED is treated as unverified
 * (covers PENDING and any future additions until they get explicit handling).
 */
export function mapReportStatus(apiStatus) {
  switch (apiStatus) {
    case 'VERIFIED': return 'verified'
    case 'FIXED':    return 'fixed'
    case 'REJECTED': return 'rejected'
    default:         return 'unverified'
  }
}

/**
 * Map API ReportResponse fields to ReportPanel prop shape.
 */
export function mapReport(r) {
  return {
    id: r.reportId,
    title: REPORT_TAGS[r.tag]?.label || r.tag,
    description: r.description,
    status: mapReportStatus(r.status),
    date: r.publishDate
      ? new Date(r.publishDate).toLocaleDateString('en-GB', {
          day: 'numeric', month: 'long', year: 'numeric',
        })
      : 'Unknown date',
    fixedAt: r.fixedAt || null,
    location: `${r.latitude.toFixed(4)}, ${r.longitude.toFixed(4)}`,
    reportedBy: `User #${r.userId}`,
    agrees: r.agrees,
    disagrees: r.disagrees,
    userVote: r.userVote ? r.userVote.toLowerCase() : null,
    tags: r.tag ? [r.tag] : [],
    image: r.mediaUrls && r.mediaUrls.length > 0 ? r.mediaUrls[0] : null,
    latitude: r.latitude,
    longitude: r.longitude,
    activeFixRequest: mapFixRequest(r.activeFixRequest),
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