import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useNotifications,
  useUnreadCount,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  notificationKeys,
} from './useNotifications.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/notificationService.js', () => ({
  getNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import { getNotifications, markNotificationRead } from '../services/notificationService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

const sample = [
  { id: 1, read: false, message: 'a' },
  { id: 2, read: true, message: 'b' },
]

describe('useNotifications module', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
  })

  describe('useNotifications', () => {
    it('disabled without auth token', () => {
      useAuth.mockReturnValue({ token: null, isAuthenticated: false })
      renderHook(() => useNotifications(), { wrapper: makeWrapper(queryClient) })
      expect(getNotifications).not.toHaveBeenCalled()
    })

    it('loads notifications', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getNotifications.mockResolvedValue(sample)

      const { result } = renderHook(() => useNotifications(), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getNotifications).toHaveBeenCalledWith('tok')
      expect(result.current.data).toEqual(sample)
    })
  })

  describe('useUnreadCount', () => {
    it('counts unread rows', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getNotifications.mockResolvedValue(sample)

      const { result } = renderHook(() => useUnreadCount(), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current).toBe(1))
    })
  })

  describe('useMarkNotificationRead', () => {
    it('calls API and rolls back on error', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getNotifications.mockResolvedValue(sample)
      markNotificationRead.mockRejectedValue(new Error('network'))

      renderHook(() => useNotifications(), { wrapper: makeWrapper(queryClient) })
      await waitFor(() => expect(getNotifications).toHaveBeenCalled())

      const { result } = renderHook(() => useMarkNotificationRead(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        try {
          await result.current.mutateAsync(1)
        } catch {
          /* expected */
        }
      })

      expect(markNotificationRead).toHaveBeenCalledWith(1, 'tok')
      await waitFor(() =>
        expect(queryClient.getQueryData(notificationKeys.list())).toEqual(sample),
      )
    })
  })

  describe('useMarkAllNotificationsRead', () => {
    it('marks every row read optimistically', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getNotifications.mockResolvedValue(sample)
      markNotificationRead.mockResolvedValue(undefined)

      renderHook(() => useNotifications(), { wrapper: makeWrapper(queryClient) })
      await waitFor(() => expect(getNotifications).toHaveBeenCalled())

      const { result } = renderHook(() => useMarkAllNotificationsRead(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync([1])
      })

      expect(markNotificationRead).toHaveBeenCalledWith(1, 'tok')
    })

    it('restores previous cache when mark-all fails', async () => {
      useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
      getNotifications.mockResolvedValue(sample)
      markNotificationRead.mockRejectedValue(new Error('network'))

      renderHook(() => useNotifications(), { wrapper: makeWrapper(queryClient) })
      await waitFor(() => expect(getNotifications).toHaveBeenCalled())

      const { result } = renderHook(() => useMarkAllNotificationsRead(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        try {
          await result.current.mutateAsync([1])
        } catch {
          /* expected */
        }
      })

      await waitFor(() =>
        expect(queryClient.getQueryData(notificationKeys.list())).toEqual(sample),
      )
    })
  })
})
