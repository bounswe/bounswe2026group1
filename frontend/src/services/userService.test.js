import { getCurrentUser, getUserById, searchUsers } from './userService.js'
import { apiFetch } from './api.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn(), currentLanguageTag: vi.fn(() => 'en') }))

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
})
