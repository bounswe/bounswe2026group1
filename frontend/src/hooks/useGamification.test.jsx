import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useUserBadges, useSetLeaderboardVisibility, userBadgesKey } from './useGamification.js'
import { currentUserKey } from './useCurrentUser.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/userService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return {
    ...mod,
    getBadges: vi.fn(),
    setLeaderboardVisibility: vi.fn(),
  }
})

import { useAuth } from '../context/AuthContext.jsx'
import { getBadges, setLeaderboardVisibility } from '../services/userService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useGamification', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    useAuth.mockReturnValue({ token: 'tok' })
  })

  describe('useUserBadges', () => {
    it('disabled without userId', () => {
      renderHook(() => useUserBadges(null), { wrapper: makeWrapper(queryClient) })
      expect(getBadges).not.toHaveBeenCalled()
    })

    it('loads badges', async () => {
      const badges = ['TOP_10']
      getBadges.mockResolvedValue(badges)

      const { result } = renderHook(() => useUserBadges(3), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getBadges).toHaveBeenCalledWith(3)
      expect(result.current.data).toEqual(badges)
      expect(queryClient.getQueryData(userBadgesKey(3))).toEqual(badges)
    })
  })

  describe('useSetLeaderboardVisibility', () => {
    it('calls service with token', async () => {
      const updated = { id: 1, leaderboardHidden: true }
      setLeaderboardVisibility.mockResolvedValue(updated)

      const { result } = renderHook(() => useSetLeaderboardVisibility(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync(true)
      })

      expect(setLeaderboardVisibility).toHaveBeenCalledWith('tok', true)
      expect(queryClient.getQueryData(currentUserKey)).toEqual(updated)
    })
  })
})
