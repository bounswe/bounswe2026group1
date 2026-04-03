const BASE_URL = import.meta.env.VITE_API_URL

export async function apiFetch(path, options = {}) {
  const { headers, ...rest } = options
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...headers },
    ...rest,
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw new Error(error.message || res.statusText)
  }

  return res.json()
}
