/**
 * SSE client for the public backend stream.
 *
 * connect(url, handlers) → close()
 *
 * handlers: {
 *   onConnected()
 *   onReportUpdated(event: PublicSseEvent)
 *   onMediaAdded(event: PublicSseEvent)
 *   onReportCreated(event: PublicSseEvent)
 *   onReportDeleted(event: PublicSseEvent)
 *   onPointsChanged(event: PointsChangedSseEvent)
 *   onStatusChange(status: 'connected' | 'reconnecting' | 'disconnected')
 * }
 *
 * Reconnect strategy: exponential backoff (1 s base, 30 s cap) with ±20% jitter.
 * Resets on each successful `connected` event from the server.
 */

const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS = 30_000
const JITTER_FACTOR = 0.2

function calcDelay(attempt) {
  const exp = Math.min(BASE_DELAY_MS * 2 ** attempt, MAX_DELAY_MS)
  const jitter = exp * JITTER_FACTOR * (Math.random() * 2 - 1)
  return Math.round(exp + jitter)
}

export function connect(url, handlers = {}) {
  const {
    onConnected,
    onReportUpdated,
    onMediaAdded,
    onReportCreated,
    onReportDeleted,
    onPointsChanged,
    onStatusChange,
  } = handlers

  let es = null
  let attempt = 0
  let retryTimer = null
  let closed = false

  function notify(status) {
    onStatusChange?.(status)
  }

  function open() {
    if (closed) return

    es = new EventSource(url)

    es.addEventListener('connected', () => {
      attempt = 0
      notify('connected')
      onConnected?.()
    })

    es.addEventListener('report-updated', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onReportUpdated?.(payload)
      } catch {
        console.warn('[SSE] Failed to parse report-updated event', e.data)
      }
    })

    es.addEventListener('media-added', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onMediaAdded?.(payload)
      } catch {
        console.warn('[SSE] Failed to parse media-added event', e.data)
      }
    })

    es.addEventListener('report-created', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onReportCreated?.(payload)
      } catch {
        console.warn('[SSE] Failed to parse report-created event', e.data)
      }
    })

    es.addEventListener('report-deleted', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onReportDeleted?.(payload)
      } catch {
        console.warn('[SSE] Failed to parse report-deleted event', e.data)
      }
    })

    es.addEventListener('points-changed', (e) => {
      try {
        const payload = JSON.parse(e.data)
        onPointsChanged?.(payload)
      } catch {
        console.warn('[SSE] Failed to parse points-changed event', e.data)
      }
    })

    es.onerror = () => {
      // EventSource closes itself on error; we handle reconnect manually
      es.close()
      if (closed) return

      if (attempt === 0) {
        // First failure after a successful connection — show reconnecting
        notify('reconnecting')
      }

      const delay = calcDelay(attempt)
      attempt++
      retryTimer = setTimeout(open, delay)
    }
  }

  notify('reconnecting') // initial state while first connect is pending
  open()

  return function close() {
    closed = true
    clearTimeout(retryTimer)
    es?.close()
    notify('disconnected')
  }
}
