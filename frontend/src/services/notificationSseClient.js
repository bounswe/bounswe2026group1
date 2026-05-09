/**
 * SSE client for the per-user notification stream.
 *
 * connect(url, handlers) → close()
 *
 * Flow:
 *  1. Call POST /api/sse/token (with Bearer header) → sets sse_token HttpOnly
 *     cookie scoped to /api/sse/notifications.
 *  2. Open EventSource to /api/sse/notifications/subscribe — cookie is sent
 *     automatically by the browser.
 *
 * handlers: {
 *   onConnected()
 *   onNotification(event: NotificationSseEvent)
 *   onStatusChange(status: 'connected' | 'reconnecting' | 'disconnected')
 * }
 *
 * Reconnect strategy: exponential backoff (1 s base, 30 s cap) with ±20%
 * jitter, matching the public SSE client.
 */

import { mintSseToken } from './notificationService.js'

const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS = 30_000
const JITTER_FACTOR = 0.2

function calcDelay(attempt) {
  const exp = Math.min(BASE_DELAY_MS * 2 ** attempt, MAX_DELAY_MS)
  const jitter = exp * JITTER_FACTOR * (Math.random() * 2 - 1)
  return Math.round(exp + jitter)
}

export function connect(url, token, handlers = {}) {
  const { onConnected, onNotification, onStatusChange } = handlers

  let es = null
  let attempt = 0
  let retryTimer = null
  let closed = false

  function notify(status) {
    onStatusChange?.(status)
  }

  async function open() {
    if (closed) return

    try {
      await mintSseToken(token)
    } catch {
      if (closed) return
      const delay = calcDelay(attempt)
      attempt++
      retryTimer = setTimeout(open, delay)
      return
    }

    if (closed) return

    // withCredentials required to send the HttpOnly sse_token cookie
    // cross-origin (different port in dev, same domain in prod).
    es = new EventSource(url, { withCredentials: true })

    es.addEventListener('connected', () => {
      attempt = 0
      notify('connected')
      onConnected?.()
    })

    es.addEventListener('notification', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onNotification?.(payload)
      } catch {
        console.warn('[NotificationSSE] Failed to parse notification event', e.data)
      }
    })

    es.onerror = () => {
      es.close()
      if (closed) return

      if (attempt === 0) {
        notify('reconnecting')
      }

      const delay = calcDelay(attempt)
      attempt++
      retryTimer = setTimeout(open, delay)
    }
  }

  notify('reconnecting')
  open()

  return function close() {
    closed = true
    clearTimeout(retryTimer)
    es?.close()
    notify('disconnected')
  }
}
