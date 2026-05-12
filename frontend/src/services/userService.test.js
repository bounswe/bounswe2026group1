import {
  getCurrentUser,
  getUserById,
  searchUsers,
  updateProfile,
  deleteAvatar,
  deleteCurrentUser,
  getBadges,
  setLeaderboardVisibility,
  getPointsHistory,
  uploadAvatar,
} from './userService.js'
import { apiFetch } from './api.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))

describe('userService', () => {
  beforeEach(() => vi.clearAllMocks())

  describe('getCurrentUser', () => {
    it('GETs /api/users/me with bearer token', async () => {
      apiFetch.mockResolvedValue({ id: 1, name: 'Alice' })
      await getCurrentUser('jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/me', {
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('getUserById', () => {
    it('GETs /api/users/:id with bearer token', async () => {
      apiFetch.mockResolvedValue({ id: 5, name: 'Bob' })
      await getUserById(5, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/5', {
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('searchUsers', () => {
    it('GETs /api/users/search with q, page, size and bearer token', async () => {
      apiFetch.mockResolvedValue({ content: [], totalElements: 0 })
      await searchUsers('alice', { page: 2, size: 10 }, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/users/search?q=alice&page=2&size=10',
        { headers: { Authorization: 'Bearer jwt' } },
      )
    })

    it('defaults page to 0 and size to 20 when options omitted', async () => {
      apiFetch.mockResolvedValue({ content: [], totalElements: 0 })
      await searchUsers('bob', undefined, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/users/search?q=bob&page=0&size=20',
        { headers: { Authorization: 'Bearer jwt' } },
      )
    })

    it('URL-encodes the query parameter', async () => {
      apiFetch.mockResolvedValue({ content: [], totalElements: 0 })
      await searchUsers('a b@c', {}, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringContaining('q=a+b%40c'),
        expect.any(Object),
      )
    })
  })

  describe('updateProfile', () => {
    it('PUTs profile body', async () => {
      apiFetch.mockResolvedValue({})
      await updateProfile(4, { name: 'Z' }, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/4/profile', {
        method: 'PUT',
        headers: { Authorization: 'Bearer jwt' },
        body: JSON.stringify({ name: 'Z' }),
      })
    })
  })

  describe('deleteAvatar', () => {
    it('DELETEs avatar', async () => {
      apiFetch.mockResolvedValue(null)
      await deleteAvatar(4, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/4/profile/avatar', {
        method: 'DELETE',
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('deleteCurrentUser', () => {
    it('DELETEs /api/users/me', async () => {
      apiFetch.mockResolvedValue(null)
      await deleteCurrentUser('jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/me', {
        method: 'DELETE',
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('getBadges', () => {
    it('GETs badges without auth header', async () => {
      apiFetch.mockResolvedValue([])
      await getBadges(12)
      expect(apiFetch).toHaveBeenCalledWith('/api/users/12/badges')
    })
  })

  describe('setLeaderboardVisibility', () => {
    it('PATCHes visibility', async () => {
      apiFetch.mockResolvedValue({})
      await setLeaderboardVisibility('jwt', true)
      expect(apiFetch).toHaveBeenCalledWith('/api/users/me/leaderboard-visibility', {
        method: 'PATCH',
        headers: { Authorization: 'Bearer jwt' },
        body: JSON.stringify({ leaderboardHidden: true }),
      })
    })
  })

  describe('getPointsHistory', () => {
    it('GETs with pagination defaults', async () => {
      apiFetch.mockResolvedValue({ content: [] })
      await getPointsHistory(8, 'jwt')
      expect(apiFetch).toHaveBeenCalledWith('/api/users/8/points/history?page=0&size=20', {
        headers: { Authorization: 'Bearer jwt' },
      })
    })
  })

  describe('uploadAvatar', () => {
    beforeEach(() => {
      vi.stubEnv('VITE_API_URL', 'http://api.test')
    })
    afterEach(() => {
      vi.unstubAllEnvs()
    })

    it('POSTs multipart and returns JSON', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ avatarUrl: 'http://x/a.png' }),
      })
      const file = new File(['x'], 'p.png', { type: 'image/png' })
      const out = await uploadAvatar(3, file, 'jwt')
      expect(global.fetch).toHaveBeenCalledWith(
        'http://api.test/api/users/3/profile/avatar',
        expect.objectContaining({
          method: 'POST',
          headers: { Authorization: 'Bearer jwt' },
          body: expect.any(FormData),
        }),
      )
      expect(out).toEqual({ avatarUrl: 'http://x/a.png' })
    })

    it('throws on error response', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        statusText: 'Bad',
        json: async () => ({ message: 'too large' }),
      })
      await expect(uploadAvatar(3, new File([], 'a'), 'jwt')).rejects.toThrow('too large')
    })
  })
})
