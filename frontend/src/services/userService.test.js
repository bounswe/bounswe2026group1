import { getCurrentUser, getUserById } from './userService.js'
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
})
