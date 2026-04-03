import { loginUser, registerUser } from './authService.js'
import { apiFetch } from './api.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('loginUser', () => {
    it('calls apiFetch with correct path, method and body', async () => {
      apiFetch.mockResolvedValue({ token: 'abc' })

      await loginUser({ email: 'a@b.com', password: 'pass' })

      expect(apiFetch).toHaveBeenCalledWith('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: 'a@b.com', password: 'pass' }),
      })
    })

    it('returns the token from the response', async () => {
      apiFetch.mockResolvedValue({ token: 'my-jwt' })

      const result = await loginUser({ email: 'a@b.com', password: 'pass' })

      expect(result).toEqual({ token: 'my-jwt' })
    })

    it('propagates errors thrown by apiFetch', async () => {
      apiFetch.mockRejectedValue(new Error('Unauthorized'))

      await expect(loginUser({ email: 'a@b.com', password: 'wrong' })).rejects.toThrow('Unauthorized')
    })
  })

  describe('registerUser', () => {
    it('calls apiFetch with correct path, method and body', async () => {
      apiFetch.mockResolvedValue({ id: 1, name: 'Alex', email: 'a@b.com', role: 'USER' })

      await registerUser({ name: 'Alex', email: 'a@b.com', password: 'pass' })

      expect(apiFetch).toHaveBeenCalledWith('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ name: 'Alex', email: 'a@b.com', password: 'pass' }),
      })
    })

    it('returns user data on success', async () => {
      const userData = { id: 1, name: 'Alex', email: 'a@b.com', role: 'USER' }
      apiFetch.mockResolvedValue(userData)

      const result = await registerUser({ name: 'Alex', email: 'a@b.com', password: 'pass' })

      expect(result).toEqual(userData)
    })

    it('propagates 409 conflict error', async () => {
      apiFetch.mockRejectedValue(new Error('Email already in use'))

      await expect(
        registerUser({ name: 'Alex', email: 'a@b.com', password: 'pass' }),
      ).rejects.toThrow('Email already in use')
    })

    it('propagates 400 bad request error', async () => {
      apiFetch.mockRejectedValue(new Error('Password is too weak'))

      await expect(
        registerUser({ name: 'Alex', email: 'a@b.com', password: '123' }),
      ).rejects.toThrow('Password is too weak')
    })
  })
})
