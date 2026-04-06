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
 * PUT /api/reports/{id}
 */
// TODO: backend should expose /agree and /disagree endpoints; using /verify and /unverify for now
export async function agreeReport(id, token) {
  return apiFetch(`/api/reports/${id}/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function disagreeReport(id, token) {
  return apiFetch(`/api/reports/${id}/unverify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * Map API ReportResponse fields to ReportPanel prop shape.
 */
export function mapReport(r) {
  return {
    id: r.reportId,
    title: REPORT_TAGS[r.tag]?.label || r.tag,
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
    tags: r.tag ? [r.tag] : [],
    image: r.mediaUrls && r.mediaUrls.length > 0 ? r.mediaUrls[0] : null,
    latitude: r.latitude,
    longitude: r.longitude,
  }
}