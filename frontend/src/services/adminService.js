import { apiFetch } from './api.js'

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

function buildQuery(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.append(key, value)
  })
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

/* -------------------------------------------------------------------------- */
/* Stats                                                                       */
/* -------------------------------------------------------------------------- */

export async function getAdminStats(token) {
  return apiFetch('/api/admin/stats', { headers: authHeaders(token) })
}

/* -------------------------------------------------------------------------- */
/* Users                                                                       */
/* -------------------------------------------------------------------------- */

export async function getAdminUsers({ status, role, page = 0, size = 20 } = {}, token) {
  return apiFetch(`/api/admin/users${buildQuery({ status, role, page, size })}`, {
    headers: authHeaders(token),
  })
}

export async function getAdminUser(id, token) {
  return apiFetch(`/api/admin/users/${id}`, { headers: authHeaders(token) })
}

export async function createAdminUser(payload, token) {
  return apiFetch('/api/admin/users', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload),
  })
}

export async function banUser(id, token) {
  return apiFetch(`/api/admin/users/${id}/ban`, {
    method: 'PATCH',
    headers: authHeaders(token),
  })
}

export async function unbanUser(id, token) {
  return apiFetch(`/api/admin/users/${id}/unban`, {
    method: 'PATCH',
    headers: authHeaders(token),
  })
}

export async function changeUserRole(id, role, token) {
  return apiFetch(`/api/admin/users/${id}/role`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ role }),
  })
}

export async function deleteAdminUser(id, token) {
  return apiFetch(`/api/admin/users/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

/* -------------------------------------------------------------------------- */
/* Reports                                                                     */
/* -------------------------------------------------------------------------- */

export async function getAdminReports(
  { status, categoryId, environment, type, dateFrom, dateTo, page = 0, size = 20 } = {},
  token,
) {
  const qs = buildQuery({ status, categoryId, environment, type, dateFrom, dateTo, page, size })
  return apiFetch(`/api/admin/reports${qs}`, { headers: authHeaders(token) })
}

export async function changeReportStatus(id, status, token) {
  return apiFetch(`/api/admin/reports/${id}/status`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ status }),
  })
}

export async function deleteAdminReport(id, token) {
  return apiFetch(`/api/admin/reports/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

/* -------------------------------------------------------------------------- */
/* Comments                                                                    */
/* -------------------------------------------------------------------------- */

export async function getAdminComments({ reportId, authorId, page = 0, size = 20 } = {}, token) {
  return apiFetch(`/api/admin/comments${buildQuery({ reportId, authorId, page, size })}`, {
    headers: authHeaders(token),
  })
}

export async function deleteAdminComment(id, token) {
  return apiFetch(`/api/admin/comments/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

/* -------------------------------------------------------------------------- */
/* Validations                                                                 */
/* -------------------------------------------------------------------------- */

export async function getAdminValidations(
  { reportId, userId, voteType, page = 0, size = 20 } = {},
  token,
) {
  return apiFetch(`/api/admin/validations${buildQuery({ reportId, userId, voteType, page, size })}`, {
    headers: authHeaders(token),
  })
}

export async function deleteAdminValidation(id, token) {
  return apiFetch(`/api/admin/validations/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}
