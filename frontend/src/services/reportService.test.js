import {
  agreeFixRequest,
  disagreeFixRequest,
  submitFixRequest,
  mapReport,
  mapFixRequest,
  mapReportStatus,
} from './reportService.js'
import { apiFetch } from './api.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))

describe('reportService — fix request helpers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('VITE_API_URL', 'http://localhost:8080')
    vi.stubEnv('VITE_API_KEY', 'test-key')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  describe('agreeFixRequest', () => {
    it('POSTs to /api/reports/{rid}/fix-requests/{fid}/agree with bearer token', async () => {
      apiFetch.mockResolvedValue({ id: 5, state: 'OPEN' })

      await agreeFixRequest(10, 5, 'jwt')

      expect(apiFetch).toHaveBeenCalledWith('/api/reports/10/fix-requests/5/agree', {
        method: 'POST',
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('disagreeFixRequest', () => {
    it('POSTs to /api/reports/{rid}/fix-requests/{fid}/disagree with bearer token', async () => {
      apiFetch.mockResolvedValue({ id: 5, state: 'OPEN' })

      await disagreeFixRequest(10, 5, 'jwt')

      expect(apiFetch).toHaveBeenCalledWith('/api/reports/10/fix-requests/5/disagree', {
        method: 'POST',
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('submitFixRequest', () => {
    it('builds multipart body and includes both auth headers', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 7, state: 'OPEN' }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      const result = await submitFixRequest(42, file, 'fixed it', 'jwt')

      expect(result).toEqual({ id: 7, state: 'OPEN' })
      const [url, opts] = fetchMock.mock.calls[0]
      expect(url).toBe('http://localhost:8080/api/reports/42/fix-requests')
      expect(opts.method).toBe('POST')
      expect(opts.headers.Authorization).toBe('Bearer jwt')
      expect(opts.headers['Mapcess-Key']).toBe('test-key')
      expect(opts.body).toBeInstanceOf(FormData)
      expect(opts.body.get('files')).toBe(file)
      expect(opts.body.get('description')).toBe('fixed it')
    })

    it('omits the description field when blank', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 7 }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      await submitFixRequest(42, file, '   ', 'jwt')

      const opts = fetchMock.mock.calls[0][1]
      expect(opts.body.has('description')).toBe(false)
    })

    it('throws an error with status set on non-OK responses', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        json: () => Promise.resolve({ message: 'duplicate' }),
        url: 'http://localhost:8080/x',
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      await expect(submitFixRequest(42, file, 'x', 'jwt')).rejects.toMatchObject({
        message: 'duplicate',
        status: 409,
      })
    })

    it('dispatches auth:expired and throws on 401', async () => {
      const dispatchSpy = vi.spyOn(window, 'dispatchEvent')
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: () => Promise.resolve({ message: 'expired' }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      await expect(submitFixRequest(42, file, null, 'jwt')).rejects.toThrow('expired')
      const events = dispatchSpy.mock.calls.map(([e]) => e.type)
      expect(events).toContain('auth:expired')
    })
  })

  describe('mapReportStatus', () => {
    it.each([
      ['VERIFIED', 'verified'],
      ['FIXED', 'fixed'],
      ['REJECTED', 'rejected'],
      ['PENDING', 'unverified'],
      [undefined, 'unverified'],
    ])('maps %s -> %s', (input, expected) => {
      expect(mapReportStatus(input)).toBe(expected)
    })
  })

  describe('mapFixRequest', () => {
    it('returns null when given null/undefined', () => {
      expect(mapFixRequest(null)).toBeNull()
      expect(mapFixRequest(undefined)).toBeNull()
    })

    it('lowercases userVote and copies through fields', () => {
      const result = mapFixRequest({
        id: 1,
        reportId: 10,
        submittedByUserId: 2,
        submittedByName: 'Alex',
        description: 'Looks fixed',
        state: 'OPEN',
        agrees: 3,
        disagrees: 1,
        createdAt: '2026-05-01T10:00:00Z',
        resolvedAt: null,
        mediaUrls: ['https://x/y.jpg'],
        userVote: 'AGREE',
      })

      expect(result).toMatchObject({
        id: 1,
        submittedByName: 'Alex',
        agrees: 3,
        disagrees: 1,
        userVote: 'agree',
        state: 'OPEN',
      })
    })
  })

  describe('mapReport', () => {
    const baseReport = {
      reportId: 1,
      userId: 99,
      latitude: 41.0,
      longitude: 29.0,
      description: 'desc',
      tag: 'MISSING_RAMP',
      status: 'VERIFIED',
      agrees: 5,
      disagrees: 1,
      publishDate: '2026-05-01T10:00:00Z',
      mediaUrls: [],
      userVote: null,
    }

    it('carries activeFixRequest through when present', () => {
      const result = mapReport({
        ...baseReport,
        activeFixRequest: {
          id: 7,
          reportId: 1,
          submittedByUserId: 2,
          submittedByName: 'Alex',
          description: null,
          state: 'OPEN',
          agrees: 0,
          disagrees: 0,
          createdAt: '2026-05-02T10:00:00Z',
          mediaUrls: [],
          userVote: null,
        },
      })

      expect(result.activeFixRequest).toMatchObject({ id: 7, state: 'OPEN' })
    })

    it('exposes null activeFixRequest when API omits it', () => {
      const result = mapReport({ ...baseReport, activeFixRequest: null })
      expect(result.activeFixRequest).toBeNull()
    })

    it('exposes fixedAt when present, null otherwise', () => {
      const fixedAt = '2026-05-03T10:00:00Z'
      expect(mapReport({ ...baseReport, fixedAt }).fixedAt).toBe(fixedAt)
      expect(mapReport({ ...baseReport }).fixedAt).toBeNull()
    })

    it('maps FIXED status to lowercase fixed', () => {
      expect(mapReport({ ...baseReport, status: 'FIXED' }).status).toBe('fixed')
    })

    it('uses a safe location label when coordinates are missing or invalid', () => {
      expect(mapReport({ ...baseReport, latitude: null, longitude: null }).location).toBe(
        'Location unavailable'
      )
      expect(mapReport({ ...baseReport, latitude: 'x', longitude: 'y' }).location).toBe(
        'Location unavailable'
      )
    })
  })
})
