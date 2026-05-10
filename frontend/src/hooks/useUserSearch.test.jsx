import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useUserSearch } from './useUserSearch.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/userService.js', () => ({
  searchUsers: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import { searchUsers } from '../services/userService.js'

function makeWrapper(queryClient) {
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useUserSearch', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
  })

  it('does not call searchUsers when query is shorter than minimum length', async () => {
    renderHook(() => useUserSearch('a'), { wrapper: makeWrapper(queryClient) })
    await new Promise((r) => setTimeout(r, 350))
    expect(searchUsers).not.toHaveBeenCalled()
  })

  it('does not call searchUsers when not authenticated', async () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    renderHook(() => useUserSearch('alice'), { wrapper: makeWrapper(queryClient) })
    await new Promise((r) => setTimeout(r, 350))
    expect(searchUsers).not.toHaveBeenCalled()
  })

  it('debounces typing and forwards the trimmed query, page, and size', async () => {
    searchUsers.mockResolvedValue({ content: [], totalElements: 0 })
    const { rerender } = renderHook(
      ({ q }) => useUserSearch(q, { page: 1, size: 5 }),
      { wrapper: makeWrapper(queryClient), initialProps: { q: '' } },
    )

    rerender({ q: '  alice  ' })
    expect(searchUsers).not.toHaveBeenCalled()

    await waitFor(
      () => expect(searchUsers).toHaveBeenCalledWith('alice', { page: 1, size: 5 }, 'tok'),
      { timeout: 1000 },
    )
  })
})
