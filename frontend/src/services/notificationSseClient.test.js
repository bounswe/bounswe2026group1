import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { connect } from './notificationSseClient.js'

vi.mock('./notificationService.js', () => ({
  mintSseToken: vi.fn(() => Promise.resolve()),
}))
import { mintSseToken } from './notificationService.js'

class FakeEventSource {
  static instance = null

  constructor(url, options) {
    this.url = url
    this.options = options
    this.listeners = {}
    this.onerror = null
    this.closed = false
    FakeEventSource.instance = this
  }

  addEventListener(type, fn) { this.listeners[type] = fn }
  close() { this.closed = true }

  emit(type, data) {
    const fn = this.listeners[type]
    if (fn) fn({ data: JSON.stringify(data) })
  }

  triggerError() {
    if (this.onerror) this.onerror(new Event('error'))
  }
}

beforeEach(() => {
  FakeEventSource.instance = null
  vi.stubGlobal('EventSource', FakeEventSource)
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('notificationSseClient.connect', () => {
  it('mints the SSE token before opening the EventSource', async () => {
    connect('http://localhost/sse/notifications', 'tok123', {})
    await vi.runAllTimersAsync()
    expect(mintSseToken).toHaveBeenCalledWith('tok123')
    expect(FakeEventSource.instance).not.toBeNull()
  })

  // Critical: EventSource must send the HttpOnly sse_token cookie cross-origin.
  it('opens EventSource with withCredentials: true', async () => {
    connect('http://localhost/sse/notifications', 'tok123', {})
    await vi.runAllTimersAsync()
    expect(FakeEventSource.instance.options).toEqual({ withCredentials: true })
  })

  it('calls onNotification with parsed payload on notification event', async () => {
    const onNotification = vi.fn()
    connect('http://localhost/sse/notifications', 'tok123', { onNotification })
    await vi.runAllTimersAsync()

    const payload = { notificationId: 5, type: 'STATUS_CHANGE', message: 'Report verified', relatedEntityId: 7, read: false }
    FakeEventSource.instance.emit('notification', payload)
    expect(onNotification).toHaveBeenCalledWith(payload)
  })

  it('calls onConnected and sets status to connected on connected event', async () => {
    const onConnected = vi.fn()
    const onStatusChange = vi.fn()
    connect('http://localhost/sse/notifications', 'tok123', { onConnected, onStatusChange })
    await vi.runAllTimersAsync()

    FakeEventSource.instance.emit('connected', '')
    expect(onConnected).toHaveBeenCalledTimes(1)
    expect(onStatusChange).toHaveBeenCalledWith('connected')
  })

  it('schedules a reconnect after EventSource error', async () => {
    connect('http://localhost/sse/notifications', 'tok123', {})
    await vi.runAllTimersAsync()
    const first = FakeEventSource.instance

    first.triggerError()
    expect(FakeEventSource.instance).toBe(first) // timer not fired yet

    await vi.runAllTimersAsync()
    expect(FakeEventSource.instance).not.toBe(first) // new instance created
  })

  it('does not reconnect after close() is called', async () => {
    const close = connect('http://localhost/sse/notifications', 'tok123', {})
    await vi.runAllTimersAsync()
    const first = FakeEventSource.instance

    close()
    first.triggerError()
    await vi.runAllTimersAsync()

    expect(FakeEventSource.instance).toBe(first) // no new EventSource
  })

  it('calls onStatusChange with disconnected when close() is called', async () => {
    const onStatusChange = vi.fn()
    const close = connect('http://localhost/sse/notifications', 'tok123', { onStatusChange })
    await vi.runAllTimersAsync()
    onStatusChange.mockClear()

    close()
    expect(onStatusChange).toHaveBeenCalledWith('disconnected')
  })

  it('retries token mint on failure and opens EventSource on the next attempt', async () => {
    mintSseToken.mockRejectedValueOnce(new Error('network error'))
    connect('http://localhost/sse/notifications', 'tok123', {})

    // Flush the rejection microtask (catch block runs, retry timer is scheduled)
    // but do NOT advance fake timers yet so EventSource hasn't been created.
    await Promise.resolve()
    expect(FakeEventSource.instance).toBeNull()

    // Fire the retry timer + flush the async open() that follows.
    await vi.runAllTimersAsync()
    expect(FakeEventSource.instance).not.toBeNull()
    expect(mintSseToken).toHaveBeenCalledTimes(2)
  })
})
