import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useRoutingPreferences,
  useUpdateRoutingPreferences,
  useCreateCustomProfile,
  useUpdateCustomProfile,
  useDeleteCustomProfile,
  useActivateCustomProfile,
  routingPreferencesKey,
} from './useRoutingPreferences.js'

vi.mock('../context/AuthContext.jsx', () => ({ useAuth: vi.fn() }))
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
  createCustomProfile,
  updateCustomProfile,
  deleteCustomProfile,
  activateCustomProfile,
} from '../services/routingPreferencesService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useRoutingPreferences hooks', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
  })

  it('does not fetch when logged out', () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    renderHook(() => useRoutingPreferences(), { wrapper: makeWrapper(queryClient) })
    expect(getRoutingPreferences).not.toHaveBeenCalled()
  })

  it('fetches when authenticated', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    const data = { preferredPreset: 'NONE' }
    getRoutingPreferences.mockResolvedValue(data)

    const { result } = renderHook(() => useRoutingPreferences(), {
      wrapper: makeWrapper(queryClient),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getRoutingPreferences).toHaveBeenCalledWith('t')
    expect(result.current.data).toEqual(data)
  })

  it('useUpdateRoutingPreferences refreshes cache', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    const next = { preferredPreset: 'WHEELCHAIR_USER' }
    updateRoutingPreferences.mockResolvedValue(next)
    const inv = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateRoutingPreferences(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ preferredPreset: 'WHEELCHAIR_USER' })
    })

    expect(updateRoutingPreferences).toHaveBeenCalled()
    expect(queryClient.getQueryData(routingPreferencesKey)).toEqual(next)
    expect(inv).toHaveBeenCalledWith({ queryKey: routingPreferencesKey })
  })

  it('useCreateCustomProfile invalidates without merging empty response', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    createCustomProfile.mockResolvedValue(undefined)
    const inv = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateCustomProfile(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ name: 'x', constraints: [] })
    })

    expect(createCustomProfile).toHaveBeenCalled()
    expect(inv).toHaveBeenCalledWith({ queryKey: routingPreferencesKey })
  })

  it('useUpdateCustomProfile invalidates', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    updateCustomProfile.mockResolvedValue(undefined)

    const { result } = renderHook(() => useUpdateCustomProfile(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ id: 3, body: { name: 'y' } })
    })

    expect(updateCustomProfile).toHaveBeenCalledWith(3, { name: 'y' }, 't')
  })

  it('useDeleteCustomProfile invalidates', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    deleteCustomProfile.mockResolvedValue(undefined)

    const { result } = renderHook(() => useDeleteCustomProfile(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(5)
    })

    expect(deleteCustomProfile).toHaveBeenCalledWith(5, 't')
  })

  it('useActivateCustomProfile passes response into refresh', async () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    const payload = { preferredPreset: 'CUSTOM', activeCustomProfileId: 1 }
    activateCustomProfile.mockResolvedValue(payload)

    const { result } = renderHook(() => useActivateCustomProfile(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(1)
    })

    expect(activateCustomProfile).toHaveBeenCalledWith(1, 't')
    expect(queryClient.getQueryData(routingPreferencesKey)).toEqual(payload)
  })
})
