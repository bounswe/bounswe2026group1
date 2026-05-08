import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider, useAuth } from './AuthContext.jsx'

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}

const wrapper = makeWrapper()

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('initial state', () => {
    it('is unauthenticated when localStorage has no token', () => {
      const { result } = renderHook(() => useAuth(), { wrapper })

      expect(result.current.isAuthenticated).toBe(false)
      expect(result.current.token).toBeNull()
    })

    it('is authenticated when a token exists in localStorage', () => {
      localStorage.setItem('token', 'existing-token')

      const { result } = renderHook(() => useAuth(), { wrapper })

      expect(result.current.isAuthenticated).toBe(true)
      expect(result.current.token).toBe('existing-token')
    })
  })

  describe('login()', () => {
    it('sets isAuthenticated to true', () => {
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.login('new-token'))

      expect(result.current.isAuthenticated).toBe(true)
    })

    it('stores the token in context', () => {
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.login('new-token'))

      expect(result.current.token).toBe('new-token')
    })

    it('persists the token to localStorage', () => {
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.login('new-token'))

      expect(localStorage.getItem('token')).toBe('new-token')
    })
  })

  describe('logout()', () => {
    it('sets isAuthenticated to false', () => {
      localStorage.setItem('token', 'some-token')
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.logout())

      expect(result.current.isAuthenticated).toBe(false)
    })

    it('clears the token from context', () => {
      localStorage.setItem('token', 'some-token')
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.logout())

      expect(result.current.token).toBeNull()
    })

    it('removes the token from localStorage', () => {
      localStorage.setItem('token', 'some-token')
      const { result } = renderHook(() => useAuth(), { wrapper })

      act(() => result.current.logout())

      expect(localStorage.getItem('token')).toBeNull()
    })
  })
})
