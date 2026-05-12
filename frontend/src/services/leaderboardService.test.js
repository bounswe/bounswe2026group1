import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getLeaderboard } from './leaderboardService.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))
import { apiFetch } from './api.js'

describe('leaderboardService', () => {
  beforeEach(() => vi.clearAllMocks())

  it('calls public leaderboard endpoint with bearer when token present', async () => {
    apiFetch.mockResolvedValue({ entries: [] })
    await getLeaderboard('jwt')
    expect(apiFetch).toHaveBeenCalledWith('/api/leaderboard', {
      headers: { Authorization: 'Bearer jwt' },
    })
  })

  it('calls without Authorization header when token absent', async () => {
    apiFetch.mockResolvedValue({ entries: [] })
    await getLeaderboard(null)
    expect(apiFetch).toHaveBeenCalledWith('/api/leaderboard', {
      headers: {},
    })
  })
})
