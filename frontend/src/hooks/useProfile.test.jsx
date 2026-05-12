import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import * as userService from '../services/userService.js'
import { AuthProvider } from '../context/AuthContext.jsx'
import { useUpdateProfile } from './useProfile.js'

vi.mock('../services/userService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return { ...mod, updateProfile: vi.fn() }
})

function jwtWithUserId(id) {
  const payload = btoa(JSON.stringify({ id }))
  return `h.${payload}.s`
}

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}

describe('useProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('useUpdateProfile', () => {
    it('calls updateProfile with user id, body, and token', async () => {
      localStorage.setItem('token', jwtWithUserId(42))
      userService.updateProfile.mockResolvedValue({ id: 42, displayName: 'New' })

      const { result } = renderHook(() => useUpdateProfile(), { wrapper: makeWrapper() })

      await act(async () => {
        await result.current.mutateAsync({ displayName: 'New' })
      })

      expect(userService.updateProfile).toHaveBeenCalledWith(
        42,
        { displayName: 'New' },
        localStorage.getItem('token'),
      )
    })

    it('exposes failure when updateProfile rejects', async () => {
      localStorage.setItem('token', jwtWithUserId(1))
      userService.updateProfile.mockRejectedValue(new Error('Conflict'))

      const { result } = renderHook(() => useUpdateProfile(), { wrapper: makeWrapper() })

      await act(async () => {
        try {
          await result.current.mutateAsync({ displayName: 'X' })
        } catch {
          /* expected */
        }
      })

      await waitFor(() => expect(result.current.isError).toBe(true))
    })
  })
})
