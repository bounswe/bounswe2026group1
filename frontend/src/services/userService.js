import { apiFetch } from './api.js'

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

export async function getCurrentUser(token) {
  return apiFetch('/api/users/me', { headers: authHeaders(token) })
}
