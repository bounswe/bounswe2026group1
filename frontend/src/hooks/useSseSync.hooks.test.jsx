import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useSseSync } from './useSseSync.js'
import { reportKeys } from './useReports.js'
import { currentUserKey } from './useCurrentUser.js'

const handlersRef = { current: null }

vi.mock('../services/sseClient.js', () => ({
  connect: vi.fn((_url, handlers) => {
    handlersRef.current = handlers
    return vi.fn()
  }),
}))

vi.mock('../context/SseContext.jsx', () => ({
  useSseStatusSetter: vi.fn(() => vi.fn()),
}))

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))

import { connect } from '../services/sseClient.js'
import { useAuth } from '../context/AuthContext.jsx'

function wrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useSseSync', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    handlersRef.current = null
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    vi.spyOn(queryClient, 'invalidateQueries')
    vi.spyOn(queryClient, 'removeQueries')
    vi.spyOn(queryClient, 'setQueryData')
    useAuth.mockReturnValue({ userId: 5 })
  })

  it('registers connect with API URL path', () => {
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    expect(connect).toHaveBeenCalled()
    expect(connect.mock.calls[0][0]).toContain('/api/sse/public/subscribe')
    expect(handlersRef.current).toBeTruthy()
  })

  it('onPointsChanged invalidates leaderboard and current user when ids match', () => {
    useAuth.mockReturnValue({ userId: 5 })
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })

    handlersRef.current.onPointsChanged({ userId: 5 })

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('onPointsChanged skips profile invalidate when different user', () => {
    useAuth.mockReturnValue({ userId: 5 })
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    vi.mocked(queryClient.invalidateQueries).mockClear()

    handlersRef.current.onPointsChanged({ userId: 99 })

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(queryClient.invalidateQueries).not.toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('onReportDeleted filters list and removes detail cache', () => {
    queryClient.setQueryData(reportKeys.lists(), [{ id: 1 }, { id: 2 }])
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })

    handlersRef.current.onReportDeleted({ reportId: 1 })

    expect(queryClient.setQueryData).toHaveBeenCalled()
    expect(queryClient.removeQueries).toHaveBeenCalledWith({
      queryKey: reportKeys.detail(1),
    })
  })

  it('onReportDeleted leaves undefined list cache unchanged', () => {
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    vi.mocked(queryClient.setQueryData).mockClear()

    handlersRef.current.onReportDeleted({ reportId: 9 })

    expect(queryClient.setQueryData).toHaveBeenCalledTimes(1)
    const [, updater] = queryClient.setQueryData.mock.calls[0]
    expect(updater(undefined)).toBeUndefined()
    expect(queryClient.removeQueries).toHaveBeenCalledWith({
      queryKey: reportKeys.detail(9),
    })
  })

  it('onPointsChanged does not invalidate profile when userId is null', () => {
    useAuth.mockReturnValue({ userId: null })
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    vi.mocked(queryClient.invalidateQueries).mockClear()

    handlersRef.current.onPointsChanged({ userId: 5 })

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
    expect(queryClient.invalidateQueries).not.toHaveBeenCalledWith({ queryKey: currentUserKey })
  })

  it('onReportCreated invalidates lists', () => {
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    handlersRef.current.onReportCreated()
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({
      queryKey: reportKeys.lists(),
    })
  })

  it('onMediaAdded invalidates report detail', () => {
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    handlersRef.current.onMediaAdded({ reportId: 44 })
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({
      queryKey: reportKeys.detail(44),
    })
  })

  it('fix operation onReportUpdated invalidates lists', () => {
    queryClient.setQueryData(reportKeys.lists(), [
      { id: 3, title: 't', status: 'unverified', agrees: 0, disagrees: 0 },
    ])
    renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
    vi.mocked(queryClient.invalidateQueries).mockClear()

    handlersRef.current.onReportUpdated({
      reportId: 3,
      operation: 'fixed',
      agrees: 0,
      disagrees: 0,
      status: 'PENDING',
    })

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({
      queryKey: reportKeys.lists(),
    })
  })

  it.each(['fix_requested', 'fix_updated'])(
    'onReportUpdated with %s invalidates lists like other fix ops',
    (operation) => {
      queryClient.setQueryData(reportKeys.lists(), [
        { id: 3, title: 't', status: 'unverified', agrees: 0, disagrees: 0 },
      ])
      renderHook(() => useSseSync(), { wrapper: wrapper(queryClient) })
      vi.mocked(queryClient.invalidateQueries).mockClear()

      handlersRef.current.onReportUpdated({
        reportId: 3,
        operation,
        agrees: 0,
        disagrees: 0,
        status: 'PENDING',
      })

      expect(queryClient.invalidateQueries).toHaveBeenCalledWith({
        queryKey: reportKeys.lists(),
      })
    },
  )
})
