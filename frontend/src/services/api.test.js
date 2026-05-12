import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

async function importApiFresh() {
  vi.resetModules()
  return import('./api.js')
}

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_URL', 'http://api.example.test')
    vi.stubEnv('VITE_API_KEY', 'secret-key')
    vi.spyOn(window, 'dispatchEvent').mockImplementation(() => true)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it('throws when VITE_API_URL is not set', async () => {
    vi.stubEnv('VITE_API_URL', '')
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/test')).rejects.toThrow(/API URL is not configured/i)
  })

  it('GET merges Mapcess-Key and omits Content-Type', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '{"a":1}',
    })
    const { apiFetch } = await importApiFresh()
    await apiFetch('/api/x')
    expect(fetch).toHaveBeenCalledWith(
      'http://api.example.test/api/x',
      expect.objectContaining({
        headers: expect.objectContaining({
          'Mapcess-Key': 'secret-key',
        }),
      }),
    )
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
  })

  it('POST with JSON body sets Content-Type when missing', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '{}',
    })
    const { apiFetch } = await importApiFresh()
    await apiFetch('/api/x', { method: 'post', body: '{"x":1}' })
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBe('application/json')
  })

  it('returns null on 204', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      text: async () => '',
    })
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/n')).resolves.toBeNull()
  })

  it('parses JSON body on success', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '{"id":2}',
    })
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/z')).resolves.toEqual({ id: 2 })
  })

  it('dispatches auth:expired on 401', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      url: 'http://api.example.test/api/users/me',
      json: async () => ({ message: 'Unauthorized' }),
    })
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/api/users/me')).rejects.toThrow()
    expect(window.dispatchEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'auth:expired' }))
  })

  it('does not dispatch auth:expired when URL looks like error endpoint', async () => {
    vi.mocked(window.dispatchEvent).mockClear()
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      url: 'http://api.example.test/error',
      json: async () => ({ message: 'x' }),
    })
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/error')).rejects.toThrow()
    expect(window.dispatchEvent).not.toHaveBeenCalledWith(expect.objectContaining({ type: 'auth:expired' }))
  })

  it('uses detail/title from error JSON', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ detail: 'Bad input' }),
    })
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/bad')).rejects.toThrow('Bad input')
  })

  it('rethrows AbortError unchanged', async () => {
    const abort = new DOMException('aborted', 'AbortError')
    global.fetch = vi.fn().mockRejectedValue(abort)
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/a')).rejects.toBe(abort)
  })

  it('wraps typical network failure', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/a')).rejects.toThrow(/cannot reach the api/i)
  })

  it('rethrows unexpected errors', async () => {
    const weird = new Error('boom')
    global.fetch = vi.fn().mockRejectedValue(weird)
    const { apiFetch } = await importApiFresh()
    await expect(apiFetch('/a')).rejects.toThrow('boom')
  })
})
