const BASE_URL = import.meta.env.VITE_API_URL
const API_KEY = import.meta.env.VITE_API_KEY

function pickErrorMessage(payload, fallback) {
  if (payload == null || typeof payload !== 'object') return fallback
  return payload.message ?? payload.detail ?? payload.title ?? fallback
}

export async function apiFetch(path, options = {}) {
  const { headers, ...rest } = options
  const method = (rest.method || 'GET').toUpperCase()
  const skipJsonContentType = method === 'GET' || method === 'HEAD'

  if (!BASE_URL) {
    throw new Error('API URL is not configured. Set VITE_API_URL (see .env.example).')
  }

  const merged = { 'Mapcess-Key': API_KEY, ...headers }
  if (skipJsonContentType) {
    delete merged['Content-Type']
  } else if (!merged['Content-Type'] && rest.body != null && typeof rest.body === 'string') {
    merged['Content-Type'] = 'application/json'
  }

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...rest,
      headers: merged,
    })
  } catch (err) {
    const name = err?.name
    const msg = err?.message || ''
    if (name === 'AbortError' || msg.includes('aborted')) {
      throw err
    }
    if (name === 'TypeError' && msg === 'Failed to fetch') {
      throw new Error(
        'Cannot reach the API. Check that the backend is running and VITE_API_URL matches it (CORS / same URL as in .env).'
      )
    }
    throw err
  }

  if (!res.ok) {
    if (res.status === 401 && !res.url.includes('/error')) {
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }
    const error = await res.json().catch(() => ({ message: res.statusText }))
    const msg = pickErrorMessage(error, res.statusText)
    throw new Error(msg)
  }

  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}
