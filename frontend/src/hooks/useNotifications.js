import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { getNotifications, markNotificationRead } from '../services/notificationService.js'

export const notificationKeys = {
  all: ['notifications'],
  list: () => [...notificationKeys.all, 'list'],
}

export function useNotifications() {
  const { token, isAuthenticated } = useAuth()

  return useQuery({
    queryKey: notificationKeys.list(),
    queryFn: () => getNotifications(token),
    enabled: isAuthenticated && !!token,
    staleTime: 60_000,
  })
}

export function useUnreadCount() {
  const { data } = useNotifications()
  if (!data) return 0
  return data.filter((n) => !n.read).length
}

export function useMarkNotificationRead() {
  const { token } = useAuth()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id) => markNotificationRead(id, token),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })
      const previous = queryClient.getQueryData(notificationKeys.list())
      queryClient.setQueryData(notificationKeys.list(), (old) =>
        old ? old.map((n) => (n.id === id ? { ...n, read: true } : n)) : old
      )
      return { previous }
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.previous !== undefined) {
        queryClient.setQueryData(notificationKeys.list(), ctx.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.list() })
    },
  })
}

export function useMarkAllNotificationsRead() {
  const { token } = useAuth()
  const queryClient = useQueryClient()

  return useMutation({
    // Caller passes the unread IDs (captured before optimistic update zeroes them out).
    mutationFn: (unreadIds) =>
      Promise.all(unreadIds.map((id) => markNotificationRead(id, token))),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.list() })
      const previous = queryClient.getQueryData(notificationKeys.list())
      queryClient.setQueryData(notificationKeys.list(), (old) =>
        old ? old.map((n) => ({ ...n, read: true })) : old
      )
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous !== undefined) {
        queryClient.setQueryData(notificationKeys.list(), ctx.previous)
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.list() })
    },
  })
}
