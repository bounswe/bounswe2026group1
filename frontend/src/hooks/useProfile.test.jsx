import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useUpdateProfile,
  useUploadAvatar,
  useDeleteAvatar,
  useDeleteAccount,
} from './useProfile.js'

vi.mock('../context/AuthContext.jsx', () => ({ useAuth: vi.fn() }))
vi.mock('../services/userService.js', () => ({
  updateProfile: vi.fn(),
  uploadAvatar: vi.fn(),
  deleteAvatar: vi.fn(),
  deleteCurrentUser: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'
import {
  updateProfile,
  uploadAvatar,
  deleteAvatar,
  deleteCurrentUser,
} from '../services/userService.js'

function wrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useProfile mutations', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false } },
    })
    queryClient.setQueryData(['currentUser'], { id: 9, name: 'Me' })
    useAuth.mockReturnValue({
      token: 'jwt',
      userId: 9,
      logout: vi.fn(),
    })
  })

  it('useUpdateProfile still invalidates when server returns no body', async () => {
    updateProfile.mockResolvedValue(null)
    const inv = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateProfile(), {
      wrapper: wrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ name: 'X' })
    })

    expect(queryClient.getQueryData(['currentUser'])).toEqual({ id: 9, name: 'Me' })
    expect(inv).toHaveBeenCalledWith({ queryKey: ['currentUser'] })
  })

  it('useUpdateProfile updates cache on success when server returns body', async () => {
    const updated = { id: 9, name: 'New' }
    updateProfile.mockResolvedValue(updated)

    const { result } = renderHook(() => useUpdateProfile(), {
      wrapper: wrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ name: 'New' })
    })

    expect(updateProfile).toHaveBeenCalledWith(9, { name: 'New' }, 'jwt')
    expect(queryClient.getQueryData(['currentUser'])).toEqual(updated)
  })

  it('useUploadAvatar invalidates current user', async () => {
    const inv = vi.spyOn(queryClient, 'invalidateQueries')
    uploadAvatar.mockResolvedValue({})

    const { result } = renderHook(() => useUploadAvatar(), {
      wrapper: wrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync(new File(['x'], 'a.png'))
    })

    expect(uploadAvatar).toHaveBeenCalled()
    expect(inv).toHaveBeenCalledWith({ queryKey: ['currentUser'] })
  })

  it('useDeleteAvatar invalidates current user', async () => {
    vi.spyOn(queryClient, 'invalidateQueries')
    deleteAvatar.mockResolvedValue(undefined)

    const { result } = renderHook(() => useDeleteAvatar(), {
      wrapper: wrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(deleteAvatar).toHaveBeenCalledWith(9, 'jwt')
  })

  it('useDeleteAccount clears cache and logs out', async () => {
    const logout = vi.fn()
    useAuth.mockReturnValue({ token: 'jwt', userId: 9, logout })
    deleteCurrentUser.mockResolvedValue(undefined)
    const clearSpy = vi.spyOn(queryClient, 'clear')

    const { result } = renderHook(() => useDeleteAccount(), {
      wrapper: wrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(deleteCurrentUser).toHaveBeenCalledWith('jwt')
    expect(clearSpy).toHaveBeenCalled()
    expect(logout).toHaveBeenCalled()
  })
})
