import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useRoutingPreferences,
  useUpdateRoutingPreferences,
  useDeleteCustomProfile,
} from './useRoutingPreferences.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/routingPreferencesService.js', () => ({
  getRoutingPreferences: vi.fn(),
  updateRoutingPreferences: vi.fn(),
  createCustomProfile: vi.fn(),
  updateCustomProfile: vi.fn(),
  deleteCustomProfile: vi.fn(),
  activateCustomProfile: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import {
  getRoutingPreferences,
  updateRoutingPreferences,
  deleteCustomProfile,
} from '../services/routingPreferencesService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useRoutingPreferences module', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
  })

  describe('useRoutingPreferences', () => {
    it('disabled when logged out', () => {
      useAuth.mockReturnValue({ token: null, isAuthenticated: false })
      renderHook(() => useRoutingPreferences(), { wrapper: makeWrapper(queryClient) })
      expect(getRoutingPreferences).not.toHaveBeenCalled()
    })

    it('loads preferences', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      const prefs = { profiles: [] }
      getRoutingPreferences.mockResolvedValue(prefs)

      const { result } = renderHook(() => useRoutingPreferences(), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getRoutingPreferences).toHaveBeenCalledWith('tok')
      expect(result.current.data).toEqual(prefs)
    })
  })

  describe('useUpdateRoutingPreferences', () => {
    it('calls updateRoutingPreferences with body and token', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getRoutingPreferences.mockResolvedValue({ profiles: [] })
      updateRoutingPreferences.mockResolvedValue({ profiles: [], avoidStairs: true })

      renderHook(() => useRoutingPreferences(), { wrapper: makeWrapper(queryClient) })
      await waitFor(() => expect(getRoutingPreferences).toHaveBeenCalled())

      const { result } = renderHook(() => useUpdateRoutingPreferences(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync({ avoidStairs: true })
      })

      expect(updateRoutingPreferences).toHaveBeenCalledWith({ avoidStairs: true }, 'tok')
    })
  })

  describe('useDeleteCustomProfile', () => {
    it('calls deleteCustomProfile', async () => {
      useAuth.mockReturnValue({ token: 'tok' })
      deleteCustomProfile.mockResolvedValue(undefined)

      const { result } = renderHook(() => useDeleteCustomProfile(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync(9)
      })

      expect(deleteCustomProfile).toHaveBeenCalledWith(9, 'tok')
    })
  })
})
