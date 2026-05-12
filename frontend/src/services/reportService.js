import { apiFetch } from './api.js'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'

/**
 * Fetch all reports from the backend.
 * GET /api/reports
 *
 * When a token is supplied the backend fills in each report's `userVote`
 * from the caller's vote history — without it, the field is always null
 * and the agree/disagree buttons can't render their selected state.
 */
export async function getReports(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  return apiFetch('/api/reports', headers ? { headers } : undefined)
}

/**
 * Fetch a single report by ID.
 * GET /api/reports/{id}
 */
export async function getReportById(id, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  return apiFetch(`/api/reports/${id}`, headers ? { headers } : undefined)
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

function formatReportLocation(latitude, longitude) {
  // `Number(null)` / `Number('')` are 0 — treat missing coords before coercing so we don't show "0.0000, 0.0000".
  if (latitude == null || longitude == null) return 'Location unavailable'
  if (latitude === '' || longitude === '') return 'Location unavailable'
  const lat = Number(latitude)
  const lon = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return 'Location unavailable'
  return `${lat.toFixed(4)}, ${lon.toFixed(4)}`
}

/**
 * Map API ReportResponse fields to ReportPanel prop shape.
 */
export function mapReport(r) {
  const objects = (r.objects || []).map(obj => ({
    objectType: obj.objectType,
    issues: obj.issues || [],
    measurements: obj.measurements
      ? (() => { try { return JSON.parse(obj.measurements) } catch { return {} } })()
      : {},
  }))

  const title = (() => {
    if (!objects.length) return r.reportType === 'FEATURE' ? 'Feature Report' : 'Obstacle Report'
    const labels = [...new Set(objects.map(o => OBJECT_TYPE_MAP[o.objectType]?.label || o.objectType))]
    return r.reportType === 'FEATURE'
      ? `${labels.join(' & ')} — Feature`
      : `${labels.join(' & ')} Issue`
  })()

  return {
    id: r.reportId,
    title,
    description: r.description,
    status: mapReportStatus(r.status),
    date: r.publishDate
      ? new Date(r.publishDate).toLocaleDateString('en-GB', {
          day: 'numeric', month: 'long', year: 'numeric',
        })
      : 'Unknown date',
    fixedAt: r.fixedAt || null,
    location: formatReportLocation(r.latitude, r.longitude),
    reportedBy: `User #${r.userId}`,
    ownerId: r.userId,
    agrees: r.agrees,
    disagrees: r.disagrees,
    userVote: r.userVote ? r.userVote.toLowerCase() : null,
    primaryObjectType: objects[0]?.objectType || null,
    reportType: r.reportType || 'OBSTACLE',
    environment: r.environment || 'OUTDOOR',
    objects,
    image: r.mediaUrls && r.mediaUrls.length > 0 ? r.mediaUrls[0] : null,
    mediaUrls: r.mediaUrls ?? [],
    latitude: r.latitude,
    longitude: r.longitude,
    activeFixRequest: mapFixRequest(r.activeFixRequest),
    authorTopBadge: r.authorTopBadge ?? null,
  }
}

/**
 * Submit a new report.
 * POST /api/reports
 */
export async function createReport({ userId, latitude, longitude, description, reportType, environment, objects, token }) {
  return apiFetch('/api/reports', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ userId, latitude, longitude, description, reportType, environment, objects }),
  })
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

export async function getReportsByUserId(userId) {
  return apiFetch(`/api/reports/user/${userId}`)
}

/**
 * Paginated community feed.
 * GET /api/reports/feed
 *
 * Filters mirror the backend `ReportFeedQuery`:
 *  - `reportType`, `environment` (single value; 'ALL' is treated as unset)
 *  - `status`, `objectType`, `issueType` (arrays; each value becomes a repeated query param)
 *  - `authorId`, `publishedAfter`, `publishedBefore`, `q`
 *  - `minAgrees`, `minDisagrees`
 *  - `sort` (omitted when falsy; backend defaults to distance with coords, newest otherwise)
 *  - `latitude` + `longitude` (+ optional `radiusInKm`)
 */
export async function getReportFeed(
  {
    page = 0,
    size = 20,
    reportType,
    environment,
    status,
    authorId,
    publishedAfter,
    publishedBefore,
    q,
    minAgrees,
    minDisagrees,
    objectType,
    issueType,
    sort,
    latitude,
    longitude,
    radiusInKm,
  },
  token,
  fetchOpts = {}
) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (reportType && reportType !== 'ALL') params.set('reportType', reportType)
  if (environment && environment !== 'ALL') params.set('environment', environment)
  appendMulti(params, 'status', status)
  if (authorId != null && Number.isFinite(Number(authorId))) {
    params.set('authorId', String(authorId))
  }
  if (publishedAfter) params.set('publishedAfter', String(publishedAfter))
  if (publishedBefore) params.set('publishedBefore', String(publishedBefore))
  const qTrimmed = typeof q === 'string' ? q.trim() : ''
  if (qTrimmed) params.set('q', qTrimmed)
  appendNonNegativeInt(params, 'minAgrees', minAgrees)
  appendNonNegativeInt(params, 'minDisagrees', minDisagrees)
  appendMulti(params, 'objectType', objectType)
  appendMulti(params, 'issueType', issueType)
  if (sort) params.set('sort', sort)
  const latN = latitude != null ? Number(latitude) : NaN
  const lonN = longitude != null ? Number(longitude) : NaN
  if (Number.isFinite(latN) && Number.isFinite(lonN)) {
    params.set('latitude', String(latN))
    params.set('longitude', String(lonN))
    if (radiusInKm != null && Number.isFinite(Number(radiusInKm))) {
      params.set('radiusInKm', String(radiusInKm))
    }
  }
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return apiFetch(`/api/reports/feed?${params.toString()}`, { headers, ...fetchOpts })
}

function appendMulti(params, key, values) {
  if (!Array.isArray(values)) return
  for (const v of values) {
    if (v != null && v !== '') params.append(key, String(v))
  }
}

function appendNonNegativeInt(params, key, value) {
  if (value == null) return
  const n = Number(value)
  if (Number.isInteger(n) && n >= 0) params.set(key, String(n))
}

export async function updateReport(id, body, token) {
  return apiFetch(`/api/reports/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
}

export async function deleteReport(id, token) {
  return apiFetch(`/api/reports/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function followReport(reportId, token) {
  return apiFetch(`/api/reports/${reportId}/follow`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function unfollowReport(reportId, token) {
  return apiFetch(`/api/reports/${reportId}/follow`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function getFollowStatus(reportId, token) {
  return apiFetch(`/api/reports/${reportId}/follow/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}