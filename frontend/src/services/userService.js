import { apiFetch } from './api.js'

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

export async function getCurrentUser(token) {
  return apiFetch('/api/users/me', { headers: authHeaders(token) })
}

export async function getUserById(id, token) {
  return apiFetch(`/api/users/${id}`, { headers: authHeaders(token) })
}

export async function searchUsers(query, { page = 0, size = 20 } = {}, token) {
  const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
  return apiFetch(`/api/users/search?${params.toString()}`, { headers: authHeaders(token) })
}

export async function updateProfile(id, body, token) {
  return apiFetch(`/api/users/${id}/profile`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
}

export async function uploadAvatar(id, file, token) {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${import.meta.env.VITE_API_URL}/api/users/${id}/profile/avatar`, {
    method: 'POST',
    headers: authHeaders(token),
    body: formData,
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw new Error(typeof error === 'string' ? error : error.message || res.statusText)
  }
  return res.json()
}

export async function deleteAvatar(id, token) {
  return apiFetch(`/api/users/${id}/profile/avatar`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

// Owner-only self-delete (#594, 1.1.1.2.12). The backend anonymizes the
// caller in place — name/email/password are scrubbed and the account is
// banned, while reports/comments/votes stay attached to the anonymized row.
export async function deleteCurrentUser(token) {
  return apiFetch('/api/users/me', {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

// ───── Gamification ──────────────────────────────────────────────────────

// Public — no token needed. Anonymous callers can view a user's badges.
export async function getBadges(id) {
  return apiFetch(`/api/users/${id}/badges`)
}

// Caller-only opt-out toggle. Body: { leaderboardHidden: boolean }.
export async function setLeaderboardVisibility(token, leaderboardHidden) {
  return apiFetch('/api/users/me/leaderboard-visibility', {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ leaderboardHidden }),
  })
}

// Owner/admin — paginated points-history ledger view.
export async function getPointsHistory(id, token, { page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch(`/api/users/${id}/points/history?${params}`, {
    headers: authHeaders(token),
  })
}
