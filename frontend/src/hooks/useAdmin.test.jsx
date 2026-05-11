import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAdminStats, useBanUser, adminKeys } from './useAdmin.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/adminService.js', () => ({
  getAdminStats: vi.fn(),
  banUser: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import { getAdminStats, banUser } from '../services/adminService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useAdmin', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
  })

  describe('useAdminStats', () => {
    it('disabled without token', () => {
      useAuth.mockReturnValue({ token: null, isAdmin: true })
      renderHook(() => useAdminStats(), { wrapper: makeWrapper(queryClient) })
      expect(getAdminStats).not.toHaveBeenCalled()
    })

    it('disabled when not admin', () => {
      useAuth.mockReturnValue({ token: 'tok', isAdmin: false })
      renderHook(() => useAdminStats(), { wrapper: makeWrapper(queryClient) })
      expect(getAdminStats).not.toHaveBeenCalled()
    })

    it('loads stats for admin', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAdmin: true })
      const stats = { totalUsers: 10, activeUsers: 8 }
      getAdminStats.mockResolvedValue(stats)

      const { result } = renderHook(() => useAdminStats(), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getAdminStats).toHaveBeenCalledWith('tok')
      expect(result.current.data).toEqual(stats)
      expect(queryClient.getQueryData(adminKeys.stats())).toEqual(stats)
    })
  })

  describe('useBanUser', () => {
    it('calls banUser with id and token', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAdmin: true })
      banUser.mockResolvedValue(undefined)

      const { result } = renderHook(() => useBanUser(), { wrapper: makeWrapper(queryClient) })

      await act(async () => {
        await result.current.mutateAsync(42)
      })

      expect(banUser).toHaveBeenCalledWith(42, 'tok')
    })
  })
})
