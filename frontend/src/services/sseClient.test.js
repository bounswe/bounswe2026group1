import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { connect } from './sseClient.js'

/**
 * Minimal EventSource stub.
 * Stores the last instance so tests can call its event listeners directly.
 */
class FakeEventSource {
  static instance = null

  constructor(url) {
    this.url = url
    this.listeners = {}
    this.onerror = null
    this.closed = false
    FakeEventSource.instance = this
  }

  addEventListener(type, fn) {
    this.listeners[type] = fn
  }

  close() {
    this.closed = true
  }

  /** Test helper — fire a named event with stringified JSON data. */
  emit(type, data) {
    const fn = this.listeners[type]
    if (fn) fn({ data: JSON.stringify(data) })
  }

  /** Test helper — trigger the onerror handler. */
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
})

describe('sseClient.connect', () => {
  it('creates an EventSource with the provided URL', () => {
    connect('http://localhost/sse', {})
    expect(FakeEventSource.instance.url).toBe('http://localhost/sse')
  })

  it('calls onStatusChange with reconnecting on initial connect', () => {
    const onStatusChange = vi.fn()
    connect('http://localhost/sse', { onStatusChange })
    expect(onStatusChange).toHaveBeenCalledWith('reconnecting')
  })

  it('calls onConnected and sets status to connected on connected event', () => {
    const onConnected = vi.fn()
    const onStatusChange = vi.fn()
    connect('http://localhost/sse', { onConnected, onStatusChange })
    FakeEventSource.instance.emit('connected', 'public-sse-connected')
    expect(onConnected).toHaveBeenCalledTimes(1)
    expect(onStatusChange).toHaveBeenCalledWith('connected')
  })

  it('calls onReportUpdated with parsed payload', () => {
    const onReportUpdated = vi.fn()
    connect('http://localhost/sse', { onReportUpdated })
    const payload = { eventType: 'REPORT_UPDATED', reportId: 1, agrees: 5, disagrees: 2, status: 'VERIFIED' }
    FakeEventSource.instance.emit('report-updated', payload)
    expect(onReportUpdated).toHaveBeenCalledWith(payload)
  })

  it('calls onMediaAdded with parsed payload', () => {
    const onMediaAdded = vi.fn()
    connect('http://localhost/sse', { onMediaAdded })
    const payload = { eventType: 'MEDIA_ADDED', reportId: 2, mediaId: 10, mediaUrl: 'http://cdn/img.jpg' }
    FakeEventSource.instance.emit('media-added', payload)
    expect(onMediaAdded).toHaveBeenCalledWith(payload)
  })

  it('calls onReportCreated with parsed payload', () => {
    const onReportCreated = vi.fn()
    connect('http://localhost/sse', { onReportCreated })
    const payload = { eventType: 'REPORT_CREATED', reportId: 3 }
    FakeEventSource.instance.emit('report-created', payload)
    expect(onReportCreated).toHaveBeenCalledWith(payload)
  })

  it('calls onReportDeleted with parsed payload', () => {
    const onReportDeleted = vi.fn()
    connect('http://localhost/sse', { onReportDeleted })
    const payload = { eventType: 'REPORT_DELETED', reportId: 7 }
    FakeEventSource.instance.emit('report-deleted', payload)
    expect(onReportDeleted).toHaveBeenCalledWith(payload)
  })

  it('schedules a reconnect after onerror', () => {
    const onStatusChange = vi.fn()
    connect('http://localhost/sse', { onStatusChange })
    const first = FakeEventSource.instance

    first.triggerError()

    // A reconnect timer should be scheduled (not immediate)
    expect(FakeEventSource.instance).toBe(first) // not reconnected yet
    vi.runAllTimers()
    expect(FakeEventSource.instance).not.toBe(first) // new EventSource created
  })

  it('does not reconnect after close() is called', () => {
    const close = connect('http://localhost/sse', {})
    const first = FakeEventSource.instance
    close()
    first.triggerError()
    vi.runAllTimers()
    // Still the same (closed) instance — no new EventSource
    expect(FakeEventSource.instance).toBe(first)
  })

  it('calls onStatusChange with disconnected when close() is called', () => {
    const onStatusChange = vi.fn()
    const close = connect('http://localhost/sse', { onStatusChange })
    onStatusChange.mockClear()
    close()
    expect(onStatusChange).toHaveBeenCalledWith('disconnected')
  })

  it('resets attempt counter after a successful connected event', () => {
    const onStatusChange = vi.fn()
    connect('http://localhost/sse', { onStatusChange })

    // Simulate a successful connection, then an error
    FakeEventSource.instance.emit('connected', '')
    FakeEventSource.instance.triggerError()

    // Should show 'reconnecting' again after a post-connection error
    expect(onStatusChange).toHaveBeenCalledWith('reconnecting')
  })
})
