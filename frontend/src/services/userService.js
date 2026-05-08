import { apiFetch } from './api.js'

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` }
}

export async function getCurrentUser(token) {
  return apiFetch('/api/users/me', { headers: authHeaders(token) })
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
