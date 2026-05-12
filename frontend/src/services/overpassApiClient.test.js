import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  executeOverpassQuery,
  DEFAULT_OVERPASS_INTERPRETER_URL,
} from './overpassApiClient.js'

describe('overpassApiClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts form-encoded query to the default interpreter URL', async () => {
    const payload = { elements: [] }
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => payload,
    })

    const result = await executeOverpassQuery('[out:json];node(1);out;')

    expect(global.fetch).toHaveBeenCalledWith(
      DEFAULT_OVERPASS_INTERPRETER_URL,
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'data=' + encodeURIComponent('[out:json];node(1);out;'),
      }),
    )
    expect(result).toEqual(payload)
  })

  it('uses custom baseUrl when provided', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({}) })

    await executeOverpassQuery('q', { baseUrl: 'https://example.test/api' })

    expect(global.fetch).toHaveBeenCalledWith(
      'https://example.test/api',
      expect.any(Object),
    )
  })

  it('passes AbortSignal to fetch', async () => {
    const ctrl = new AbortController()
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({}) })

    await executeOverpassQuery('q', { signal: ctrl.signal })

    expect(global.fetch.mock.calls[0][1].signal).toBe(ctrl.signal)
  })

  it('throws with status detail when response is not ok', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 504,
      text: async () => 'timeout',
    })

    await expect(executeOverpassQuery('x')).rejects.toThrow(/Overpass HTTP 504/)
  })

  it('throws without body detail when text() fails', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 500,
      text: async () => {
        throw new Error('read failed')
      },
    })

    await expect(executeOverpassQuery('x')).rejects.toThrow(/Overpass HTTP 500[^:]*$/)
  })
})
