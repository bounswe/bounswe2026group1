import { apiFetch } from './api.js'

export async function loginUser({ email, password }) {
  return apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export async function registerUser({ name, email, password }) {
  return apiFetch('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  })
}
