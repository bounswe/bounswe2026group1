/**
 * Production Overpass API client for OSM queries (frontend runtime; mirrors backend OverpassService).
 * Automated tests mock global `fetch` and do not exercise live Overpass integration.
 */

export const DEFAULT_OVERPASS_INTERPRETER_URL = 'https://overpass-api.de/api/interpreter'

/**
 * @param {string} overpassQl — Overpass QL query string
 * @param {{ baseUrl?: string, signal?: AbortSignal }} [options]
 * @returns {Promise<unknown>} Parsed JSON response
 */
export async function executeOverpassQuery(overpassQl, options = {}) {
  const url = options.baseUrl ?? DEFAULT_OVERPASS_INTERPRETER_URL
  const body = `data=${encodeURIComponent(overpassQl)}`
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
    signal: options.signal,
  })
  if (!res.ok) {
    let detail = ''
    try {
      detail = await res.text()
    } catch {
      /* ignore */
    }
    throw new Error(`Overpass HTTP ${res.status}${detail ? `: ${detail.slice(0, 200)}` : ''}`)
  }
  return res.json()
}
