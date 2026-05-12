import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLeaderboard, leaderboardKey } from './useLeaderboard.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/leaderboardService.js', () => ({
  getLeaderboard: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import { getLeaderboard } from '../services/leaderboardService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useLeaderboard', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('uses anon query key when logged out', async () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    getLeaderboard.mockResolvedValue({ entries: [] })

    const { result } = renderHook(() => useLeaderboard(), { wrapper: makeWrapper(queryClient) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getLeaderboard).toHaveBeenCalledWith(null)
    expect(queryClient.getQueryCache().find({ queryKey: leaderboardKey(false) })).toBeTruthy()
  })

  it('passes token when authenticated', async () => {
    useAuth.mockReturnValue({ token: 'jwt', isAuthenticated: true })
    getLeaderboard.mockResolvedValue({ entries: [], callerRank: 3 })

    const { result } = renderHook(() => useLeaderboard(), { wrapper: makeWrapper(queryClient) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getLeaderboard).toHaveBeenCalledWith('jwt')
    expect(queryClient.getQueryCache().find({ queryKey: leaderboardKey(true) })).toBeTruthy()
  })
})
