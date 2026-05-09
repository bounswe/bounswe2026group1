import { apiFetch } from './api.js'

export function getNotifications(token) {
  return apiFetch('/api/notifications', {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function markNotificationRead(id, token) {
  return apiFetch(`/api/notifications/${id}/read`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function mintSseToken(token) {
  return apiFetch('/api/sse/token', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    // credentials: 'include' is required so the browser stores the HttpOnly
    // sse_token cookie from the cross-origin response.
    credentials: 'include',
  })
}
