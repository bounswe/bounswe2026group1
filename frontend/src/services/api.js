const BASE_URL = import.meta.env.VITE_API_URL
const API_KEY = import.meta.env.VITE_API_KEY

function apiBaseMissingMessage() {
  return (
    'API base URL is not configured (VITE_API_URL). ' +
    'Create frontend/.env from frontend/.env.example and set VITE_API_URL to your backend (e.g. http://localhost:8080), then restart the dev server.'
  )
}

/**
 * Pull a useful message from Spring / RFC7807 error bodies (message/detail/title).
 */
function parseErrorBody(body, fallback) {
  if (!body || typeof body !== 'object') return fallback
  return (
    body.message ||
    body.detail ||
    body.title ||
    (typeof body.status === 'number' ? `${body.status}` : null) ||
    fallback
  )
}

export async function apiFetch(path, options = {}) {
  if (!BASE_URL || BASE_URL === 'undefined') {
    throw new Error(apiBaseMissingMessage())
  }

  const { headers, ...rest } = options
  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...rest,
      headers: { 'Content-Type': 'application/json', 'Mapcess-Key': API_KEY ?? '', ...headers },
    })
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    throw new Error(
      `Cannot reach API at ${BASE_URL}${path}. Start the backend (e.g. ./mvnw spring-boot:run) or fix VITE_API_URL. (${msg})`
    )
  }

  if (!res.ok) {
    if (res.status === 401 && !res.url.includes('/error')) {
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }
    const body = await res.json().catch(() => null)
    const piece = parseErrorBody(body, res.statusText)
    throw new Error(piece ? `${res.status} ${piece}` : `${res.status} ${res.statusText}`)
  }

  if (res.status === 204) return null
  const text = await res.text()
  try {
    return text ? JSON.parse(text) : null
  } catch {
    throw new Error(
      `Invalid JSON from API (${path}). Check VITE_API_URL points to the MapCess backend, not an HTML page.`
    )
  }
}
