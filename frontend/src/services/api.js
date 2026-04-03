const BASE_URL = import.meta.env.VITE_API_URL

export async function apiFetch(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw new Error(error.message || res.statusText)
  }

  return res.json()
}
