import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useCurrentUser, currentUserKey } from './useCurrentUser.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/userService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return { ...mod, getCurrentUser: vi.fn() }
})

import { useAuth } from '../context/AuthContext.jsx'
import { getCurrentUser } from '../services/userService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useCurrentUser', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('does not fetch when unauthenticated', async () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    renderHook(() => useCurrentUser(), { wrapper: makeWrapper(queryClient) })
    await waitFor(() => expect(getCurrentUser).not.toHaveBeenCalled())
  })

  it('fetches current user when authenticated', async () => {
    useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
    const user = { id: 1, name: 'Me' }
    getCurrentUser.mockResolvedValue(user)

    const { result } = renderHook(() => useCurrentUser(), { wrapper: makeWrapper(queryClient) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getCurrentUser).toHaveBeenCalledWith('tok')
    expect(result.current.data).toEqual(user)
    expect(queryClient.getQueryData(currentUserKey)).toEqual(user)
  })
})
