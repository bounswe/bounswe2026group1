import { apiFetch } from './api.js'

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

// ── Routing preferences (built-in presets + active state) ──────────────────

export async function getRoutingPreferences(token) {
  return apiFetch('/api/users/me/routing-preferences', { headers: authHeaders(token) })
}

export async function updateRoutingPreferences(body, token) {
  return apiFetch('/api/users/me/routing-preferences', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
}

// ── Custom routing profiles (per-user named bundles, max 5) ────────────────

export async function listCustomProfiles(token) {
  return apiFetch('/api/users/me/routing-profiles', { headers: authHeaders(token) })
}

export async function createCustomProfile(body, token) {
  return apiFetch('/api/users/me/routing-profiles', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
}

export async function updateCustomProfile(id, body, token) {
  return apiFetch(`/api/users/me/routing-profiles/${id}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
}

export async function deleteCustomProfile(id, token) {
  return apiFetch(`/api/users/me/routing-profiles/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
}

export async function activateCustomProfile(id, token) {
  return apiFetch(`/api/users/me/routing-profiles/${id}/activate`, {
    method: 'POST',
    headers: authHeaders(token),
  })
}
