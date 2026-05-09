import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { connect } from '../services/notificationSseClient.js'
import { notificationKeys } from './useNotifications.js'

const SSE_URL = `${import.meta.env.VITE_API_URL}/api/sse/notifications/subscribe`

/**
 * Mount once at app root (when authenticated).
 * Opens the per-user notification SSE channel and prepends new notifications
 * into the React Query cache in real time.
 */
export function useNotificationSseSync() {
  const { token, isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!isAuthenticated || !token) return

    const close = connect(SSE_URL, token, {
      onNotification(event) {
        queryClient.setQueryData(notificationKeys.list(), (old) => {
          const notification = {
            id: event.notificationId,
            recipientId: event.recipientId,
            type: event.type,
            message: event.message,
            relatedEntityId: event.relatedEntityId,
            read: event.read,
            createdAt: event.createdAt,
          }
          if (!old) return [notification]
          // Avoid duplicates (e.g. on reconnect)
          if (old.some((n) => n.id === notification.id)) return old
          return [notification, ...old]
        })
      },
    })

    return close
  }, [isAuthenticated, token, queryClient])
}
