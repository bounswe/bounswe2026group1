import { apiFetch } from './api.js'

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * Fetches the public leaderboard payload — top-N entries plus the caller's
 * own rank when authenticated. The endpoint is public, so token is optional;
 * callers pass it through so the backend can populate `callerRank` in the
 * response (it stays null for anonymous viewers and opt-out users).
 */
export async function getLeaderboard(token) {
  return apiFetch('/api/leaderboard', { headers: authHeaders(token) })
}
