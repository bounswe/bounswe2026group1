import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useUserProfile } from './useUserProfile.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/userService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return { ...mod, getUserById: vi.fn() }
})

import { useAuth } from '../context/AuthContext.jsx'
import { getUserById } from '../services/userService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useUserProfile', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('is disabled without userId', () => {
    useAuth.mockReturnValue({ token: 't', isAuthenticated: true })
    renderHook(() => useUserProfile(null), { wrapper: makeWrapper(queryClient) })
    expect(getUserById).not.toHaveBeenCalled()
  })

  it('is disabled when unauthenticated', () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    renderHook(() => useUserProfile(5), { wrapper: makeWrapper(queryClient) })
    expect(getUserById).not.toHaveBeenCalled()
  })

  it('loads profile by id when authenticated', async () => {
    useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
    const profile = { id: 5, name: 'Ada' }
    getUserById.mockResolvedValue(profile)

    const { result } = renderHook(() => useUserProfile(5), { wrapper: makeWrapper(queryClient) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getUserById).toHaveBeenCalledWith(5, 'tok')
    expect(result.current.data).toEqual(profile)
  })
})
