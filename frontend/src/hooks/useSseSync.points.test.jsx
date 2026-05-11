import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useSseSync } from './useSseSync.js'
import { currentUserKey } from './useCurrentUser.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../context/SseContext.jsx', () => ({
  useSseStatusSetter: () => vi.fn(),
}))
vi.mock('../services/sseClient.js', () => ({
  connect: vi.fn(() => vi.fn()),
}))

import { useAuth } from '../context/AuthContext.jsx'
import { connect } from '../services/sseClient.js'

function makeWrapper(queryClient) {
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useSseSync — onPointsChanged', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('invalidates the leaderboard query for any users points change', () => {
    useAuth.mockReturnValue({ userId: 100 })
    const spy = vi.spyOn(queryClient, 'invalidateQueries')

    renderHook(() => useSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onPointsChanged } = connect.mock.calls[0][1]

    // A different user's points changed — leaderboard still invalidated
    // because rankings shift, but profile is not.
    onPointsChanged({ eventType: 'POINTS_CHANGED', userId: 7, points: 30 })

    expect(spy).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(spy).not.toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('also invalidates currentUser when the callers own points change', () => {
    useAuth.mockReturnValue({ userId: 42 })
    const spy = vi.spyOn(queryClient, 'invalidateQueries')

    renderHook(() => useSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onPointsChanged } = connect.mock.calls[0][1]

    onPointsChanged({ eventType: 'POINTS_CHANGED', userId: 42, points: 55 })

    expect(spy).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(spy).toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('matches userId across number/string mismatches between JWT and JSON payload', () => {
    // JWT claim is a number; SSE payload may serialize the same id as a
    // string. The hook should still detect the match.
    useAuth.mockReturnValue({ userId: 42 })
    const spy = vi.spyOn(queryClient, 'invalidateQueries')

    renderHook(() => useSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onPointsChanged } = connect.mock.calls[0][1]

    onPointsChanged({ eventType: 'POINTS_CHANGED', userId: '42', points: 55 })

    expect(spy).toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('does not invalidate currentUser when no caller is logged in', () => {
    useAuth.mockReturnValue({ userId: null })
    const spy = vi.spyOn(queryClient, 'invalidateQueries')

    renderHook(() => useSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onPointsChanged } = connect.mock.calls[0][1]

    onPointsChanged({ eventType: 'POINTS_CHANGED', userId: 42, points: 55 })

    expect(spy).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(spy).not.toHaveBeenCalledWith({ queryKey: currentUserKey })
  })
})
