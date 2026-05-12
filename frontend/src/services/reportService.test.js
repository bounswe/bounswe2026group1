import {
  agreeFixRequest,
  disagreeFixRequest,
  submitFixRequest,
  getReportFeed,
  mapReport,
  mapFixRequest,
  mapReportStatus,
  contributeMeasurements,
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
    it('builds multipart body with multiple files and includes both auth headers', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 7, state: 'OPEN' }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file1 = new File(['bytes1'], 'fix1.jpg', { type: 'image/jpeg' })
      const file2 = new File(['bytes2'], 'fix2.jpg', { type: 'image/jpeg' })

      const result = await submitFixRequest(42, [file1, file2], 'fixed it', 'jwt')

      expect(result).toEqual({ id: 7, state: 'OPEN' })
      const [url, opts] = fetchMock.mock.calls[0]
      expect(url).toBe('http://localhost:8080/api/reports/42/fix-requests')
      expect(opts.method).toBe('POST')
      expect(opts.headers.Authorization).toBe('Bearer jwt')
      expect(opts.headers['Mapcess-Key']).toBe('test-key')
      expect(opts.body).toBeInstanceOf(FormData)
      expect(opts.body.getAll('files')).toHaveLength(2)
      expect(opts.body.getAll('files')[0]).toBe(file1)
      expect(opts.body.getAll('files')[1]).toBe(file2)
      expect(opts.body.get('description')).toBe('fixed it')
    })

    it('handles a single File for backward compatibility', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 8, state: 'OPEN' }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      await submitFixRequest(42, file, 'done', 'jwt')

      const opts = fetchMock.mock.calls[0][1]
      expect(opts.body.getAll('files')).toHaveLength(1)
      expect(opts.body.getAll('files')[0]).toBe(file)
    })

    it('omits the description field when blank', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 7 }),
      })
      vi.stubGlobal('fetch', fetchMock)
      const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })

      await submitFixRequest(42, [file], '   ', 'jwt')

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

      await expect(submitFixRequest(42, [file], 'x', 'jwt')).rejects.toMatchObject({
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

      await expect(submitFixRequest(42, [file], null, 'jwt')).rejects.toThrow('expired')
      const events = dispatchSpy.mock.calls.map(([e]) => e.type)
      expect(events).toContain('auth:expired')
    })
  })

  describe('getReportFeed', () => {
    it('GETs /api/reports/feed with page and size; omits ALL filters and coords', async () => {
      apiFetch.mockResolvedValue({ content: [], last: true })

      await getReportFeed(
        { page: 0, size: 20, reportType: 'ALL', environment: 'ALL' },
        null
      )

      expect(apiFetch).toHaveBeenCalledWith('/api/reports/feed?page=0&size=20', { headers: {} })
    })

    it('adds reportType and environment when not ALL', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed(
        { page: 1, size: 10, reportType: 'OBSTACLE', environment: 'OUTDOOR' },
        null
      )

      const qs = apiFetch.mock.calls[0][0].split('?')[1]
      const sp = new URLSearchParams(qs)
      expect(sp.get('page')).toBe('1')
      expect(sp.get('size')).toBe('10')
      expect(sp.get('reportType')).toBe('OBSTACLE')
      expect(sp.get('environment')).toBe('OUTDOOR')
    })

    it('adds latitude, longitude, and radiusInKm when coordinates are finite', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed(
        {
          page: 0,
          size: 20,
          reportType: 'ALL',
          environment: 'ALL',
          latitude: 41.08,
          longitude: 29.04,
          radiusInKm: 5,
        },
        null
      )

      const qs = apiFetch.mock.calls[0][0].split('?')[1]
      const sp = new URLSearchParams(qs)
      expect(sp.get('latitude')).toBe('41.08')
      expect(sp.get('longitude')).toBe('29.04')
      expect(sp.get('radiusInKm')).toBe('5')
    })

    it('sends Bearer token when provided', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed({ page: 0, size: 20 }, 'secret-token')

      expect(apiFetch).toHaveBeenCalledWith(expect.any(String), {
        headers: { Authorization: 'Bearer secret-token' },
      })
    })

    it('merges fetchOpts into apiFetch options', async () => {
      apiFetch.mockResolvedValue({ content: [] })
      const ac = new AbortController()

      await getReportFeed({ page: 0, size: 20 }, null, { signal: ac.signal })

      expect(apiFetch).toHaveBeenCalledWith(expect.any(String), {
        headers: {},
        signal: ac.signal,
      })
    })

    it('serializes multi-value enum filters as repeated query params', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed(
        {
          page: 0,
          size: 20,
          status: ['VERIFIED', 'PENDING'],
          objectType: ['RAMP'],
          objectIssue: ['RAMP:TOO_STEEP', 'RAMP:MISSING'],
        },
        null
      )

      const qs = apiFetch.mock.calls[0][0].split('?')[1]
      const sp = new URLSearchParams(qs)
      expect(sp.getAll('status')).toEqual(['VERIFIED', 'PENDING'])
      expect(sp.getAll('objectType')).toEqual(['RAMP'])
      expect(sp.getAll('objectIssue')).toEqual(['RAMP:TOO_STEEP', 'RAMP:MISSING'])
    })

    it('passes authorId, date range, q, and vote thresholds', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed(
        {
          page: 0,
          size: 20,
          authorId: 42,
          publishedAfter: '2026-01-01T00:00:00Z',
          publishedBefore: '2026-06-01T00:00:00Z',
          q: '  elevator  ',
          minAgrees: 3,
          minDisagrees: 0,
        },
        null
      )

      const qs = apiFetch.mock.calls[0][0].split('?')[1]
      const sp = new URLSearchParams(qs)
      expect(sp.get('authorId')).toBe('42')
      expect(sp.get('publishedAfter')).toBe('2026-01-01T00:00:00Z')
      expect(sp.get('publishedBefore')).toBe('2026-06-01T00:00:00Z')
      expect(sp.get('q')).toBe('elevator') // trimmed
      expect(sp.get('minAgrees')).toBe('3')
      expect(sp.get('minDisagrees')).toBe('0')
    })

    it('sends sort when explicit; omits sort when falsy', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed({ page: 0, size: 20, sort: 'MOST_AGREED' }, null)
      let qs = apiFetch.mock.calls[0][0].split('?')[1]
      expect(new URLSearchParams(qs).get('sort')).toBe('MOST_AGREED')

      apiFetch.mockClear()
      await getReportFeed({ page: 0, size: 20, sort: undefined }, null)
      qs = apiFetch.mock.calls[0][0].split('?')[1]
      expect(new URLSearchParams(qs).has('sort')).toBe(false)
    })

    it('drops empty arrays, whitespace-only q, and negative vote thresholds', async () => {
      apiFetch.mockResolvedValue({ content: [] })

      await getReportFeed(
        {
          page: 0,
          size: 20,
          status: [],
          objectType: [null, ''],
          q: '   ',
          minAgrees: -1,
          minDisagrees: '',
        },
        null
      )

      const qs = apiFetch.mock.calls[0][0].split('?')[1]
      const sp = new URLSearchParams(qs)
      expect(sp.has('status')).toBe(false)
      expect(sp.has('objectType')).toBe(false)
      expect(sp.has('q')).toBe(false)
      expect(sp.has('minAgrees')).toBe(false)
      expect(sp.has('minDisagrees')).toBe(false)
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

  describe('contributeMeasurements', () => {
    it('PATCHes /api/reports/{rid}/objects/{oid}/measurements with bearer token and values', async () => {
      apiFetch.mockResolvedValue({ reportId: 10, objects: [] })

      await contributeMeasurements(10, 5, { width_cm: 120 }, 'jwt')

      expect(apiFetch).toHaveBeenCalledWith('/api/reports/10/objects/5/measurements', {
        method: 'PATCH',
        headers: { Authorization: 'Bearer jwt' },
        body: JSON.stringify({ values: { width_cm: 120 } }),
      })
    })

    it('returns the raw API response so the caller can pass it to mapReport', async () => {
      const raw = { reportId: 10, objects: [], status: 'PENDING' }
      apiFetch.mockResolvedValue(raw)

      const result = await contributeMeasurements(10, 5, { width_cm: 120 }, 'jwt')

      expect(result).toBe(raw)
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

    it('carries mediaIds from the API response so edit mode can pair them with URLs', () => {
      const result = mapReport({
        ...baseReport,
        mediaIds: [101, 102],
        mediaUrls: ['https://cdn.example.com/a.jpg', 'https://cdn.example.com/b.jpg'],
      })
      expect(result.mediaIds).toEqual([101, 102])
      expect(result.mediaUrls).toEqual(['https://cdn.example.com/a.jpg', 'https://cdn.example.com/b.jpg'])
    })

    it('includes the object id so the PATCH endpoint can be called per-object', () => {
      const result = mapReport({
        ...baseReport,
        objects: [
          { id: 7, objectType: 'RAMP', issues: ['TOO_STEEP'], measurements: '{"slope_percent":8}' },
        ],
      })
      expect(result.objects[0].id).toBe(7)
    })

    it('parses measurements JSON string into an object', () => {
      const result = mapReport({
        ...baseReport,
        objects: [
          { id: 1, objectType: 'RAMP', issues: [], measurements: '{"slope_percent":8,"width_cm":120}' },
        ],
      })
      expect(result.objects[0].measurements).toEqual({ slope_percent: 8, width_cm: 120 })
    })

    it('returns an empty object for measurements when the field is absent', () => {
      const result = mapReport({
        ...baseReport,
        objects: [
          { id: 1, objectType: 'RAMP', issues: [], measurements: null },
        ],
      })
      expect(result.objects[0].measurements).toEqual({})
    })

    it('returns an empty object when measurements JSON is malformed', () => {
      const result = mapReport({
        ...baseReport,
        objects: [
          { id: 1, objectType: 'RAMP', issues: [], measurements: '{bad json' },
        ],
      })
      expect(result.objects[0].measurements).toEqual({})
    })
  })
})
