import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useNotificationSseSync } from './useNotificationSseSync.js'
import { notificationKeys } from './useNotifications.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/notificationSseClient.js', () => ({
  connect: vi.fn(() => vi.fn()), // returns a no-op close fn by default
}))

import { useAuth } from '../context/AuthContext.jsx'
import { connect } from '../services/notificationSseClient.js'

function makeWrapper(queryClient) {
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useNotificationSseSync', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
  })

  it('calls connect with the user token when authenticated', () => {
    renderHook(() => useNotificationSseSync(), { wrapper: makeWrapper(queryClient) })
    expect(connect).toHaveBeenCalledWith(
      expect.any(String),
      'tok',
      expect.objectContaining({ onNotification: expect.any(Function) }),
    )
  })

  it('does not call connect when not authenticated', () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    renderHook(() => useNotificationSseSync(), { wrapper: makeWrapper(queryClient) })
    expect(connect).not.toHaveBeenCalled()
  })

  it('prepends a new notification to the React Query cache with the correct shape', () => {
    const existing = { id: 1, message: 'old notification', read: false }
    queryClient.setQueryData(notificationKeys.list(), [existing])

    renderHook(() => useNotificationSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onNotification } = connect.mock.calls[0][2]

    onNotification({
      notificationId: 2,
      recipientId: 42,
      type: 'NEW_COMMENT',
      message: 'New comment on your report',
      relatedEntityId: 5,
      read: false,
      createdAt: '2026-05-09T10:00:00Z',
    })

    const cached = queryClient.getQueryData(notificationKeys.list())
    expect(cached).toHaveLength(2)
    expect(cached[0]).toEqual({
      id: 2,
      recipientId: 42,
      type: 'NEW_COMMENT',
      message: 'New comment on your report',
      relatedEntityId: 5,
      read: false,
      createdAt: '2026-05-09T10:00:00Z',
    })
    expect(cached[1]).toEqual(existing)
  })

  it('skips duplicate notifications (same notificationId)', () => {
    const existing = { id: 2, message: 'existing', read: false }
    queryClient.setQueryData(notificationKeys.list(), [existing])

    renderHook(() => useNotificationSseSync(), { wrapper: makeWrapper(queryClient) })
    const { onNotification } = connect.mock.calls[0][2]

    onNotification({ notificationId: 2, type: 'NEW_COMMENT', message: 'existing', relatedEntityId: 5, read: false, createdAt: '2026-05-09T10:00:00Z' })

    const cached = queryClient.getQueryData(notificationKeys.list())
    expect(cached).toHaveLength(1)
  })

  it('calls the close function returned by connect on unmount', () => {
    const closeFn = vi.fn()
    connect.mockReturnValue(closeFn)

    const { unmount } = renderHook(() => useNotificationSseSync(), { wrapper: makeWrapper(queryClient) })
    unmount()

    expect(closeFn).toHaveBeenCalledTimes(1)
  })
})
