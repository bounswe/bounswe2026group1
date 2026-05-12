import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  adminKeys,
  useAdminStats,
  useAdminUsersQuery,
  useAdminUser,
  useAdminReportsQuery,
  useAdminCommentsQuery,
  useAdminValidationsQuery,
  useBanUser,
  useUnbanUser,
  useChangeUserRole,
  useDeleteAdminUser,
  useCreateAdminUser,
  useChangeReportStatus,
  useDeleteAdminReport,
  useDeleteAdminComment,
  useDeleteAdminValidation,
} from './useAdmin.js'

const adminFns = vi.hoisted(() => ({
  getAdminStats: vi.fn(),
  getAdminUsers: vi.fn(),
  getAdminUser: vi.fn(),
  getAdminReports: vi.fn(),
  getAdminComments: vi.fn(),
  getAdminValidations: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn(),
  changeUserRole: vi.fn(),
  deleteAdminUser: vi.fn(),
  createAdminUser: vi.fn(),
  changeReportStatus: vi.fn(),
  deleteAdminReport: vi.fn(),
  deleteAdminComment: vi.fn(),
  deleteAdminValidation: vi.fn(),
}))

vi.mock('../context/AuthContext.jsx', () => ({ useAuth: vi.fn() }))
vi.mock('../services/adminService.js', () => adminFns)

import { useAuth } from '../context/AuthContext.jsx'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useAdmin', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
  })

  describe('adminKeys', () => {
    it('builds stable query keys', () => {
      expect(adminKeys.stats()).toEqual(['admin', 'stats'])
      expect(adminKeys.users({ q: 'a' })).toEqual(['admin', 'users', { q: 'a' }])
      expect(adminKeys.user(7)).toEqual(['admin', 'user', 7])
      expect(adminKeys.reports({ page: 1 })).toEqual(['admin', 'reports', { page: 1 }])
      expect(adminKeys.comments()).toEqual(['admin', 'comments', {}])
      expect(adminKeys.validations(null)).toEqual(['admin', 'validations', {}])
    })
  })

  describe('queries', () => {
    const adminAuth = { token: 'tok', isAdmin: true }

    it('useAdminStats loads for admin', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminStats.mockResolvedValue({ totalUsers: 1 })

      const { result } = renderHook(() => useAdminStats(), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminStats).toHaveBeenCalledWith('tok')
    })

    it('useAdminUsersQuery respects filters', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminUsers.mockResolvedValue([])

      const { result } = renderHook(() => useAdminUsersQuery({ page: 2 }), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminUsers).toHaveBeenCalledWith({ page: 2 }, 'tok')
    })

    it('useAdminUser disabled without id', () => {
      useAuth.mockReturnValue(adminAuth)
      renderHook(() => useAdminUser(null), { wrapper: makeWrapper(queryClient) })
      expect(adminFns.getAdminUser).not.toHaveBeenCalled()
    })

    it('useAdminUser loads single user', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminUser.mockResolvedValue({ id: 3 })

      const { result } = renderHook(() => useAdminUser(3), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminUser).toHaveBeenCalledWith(3, 'tok')
    })

    it('useAdminReportsQuery', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminReports.mockResolvedValue([])

      const { result } = renderHook(() => useAdminReportsQuery({}), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminReports).toHaveBeenCalled()
    })

    it('useAdminCommentsQuery', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminComments.mockResolvedValue([])

      const { result } = renderHook(() => useAdminCommentsQuery({}), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminComments).toHaveBeenCalled()
    })

    it('useAdminValidationsQuery', async () => {
      useAuth.mockReturnValue(adminAuth)
      adminFns.getAdminValidations.mockResolvedValue([])

      const { result } = renderHook(() => useAdminValidationsQuery({}), {
        wrapper: makeWrapper(queryClient),
      })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(adminFns.getAdminValidations).toHaveBeenCalled()
    })

    it('queries disabled when not admin', () => {
      useAuth.mockReturnValue({ token: 'tok', isAdmin: false })
      renderHook(() => useAdminStats(), { wrapper: makeWrapper(queryClient) })
      expect(adminFns.getAdminStats).not.toHaveBeenCalled()
    })
  })

  describe('mutations', () => {
    const auth = { token: 'tok', isAdmin: true }

    async function expectInvalidateAfter(mutateHook, mockFn, setupMs) {
      useAuth.mockReturnValue(auth)
      mockFn.mockResolvedValue(undefined)
      const inv = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(mutateHook, {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await setupMs(result)
      })

      expect(mockFn).toHaveBeenCalled()
      expect(inv).toHaveBeenCalled()
    }

    it('useBanUser invalidates users + stats', async () => {
      await expectInvalidateAfter(
        () => useBanUser(),
        adminFns.banUser,
        async (r) => r.current.mutateAsync(1),
      )
    })

    it('useUnbanUser', async () => {
      await expectInvalidateAfter(
        () => useUnbanUser(),
        adminFns.unbanUser,
        async (r) => r.current.mutateAsync(2),
      )
    })

    it('useChangeUserRole', async () => {
      await expectInvalidateAfter(
        () => useChangeUserRole(),
        adminFns.changeUserRole,
        async (r) => r.current.mutateAsync({ id: 3, role: 'ADMIN' }),
      )
    })

    it('useDeleteAdminUser', async () => {
      await expectInvalidateAfter(
        () => useDeleteAdminUser(),
        adminFns.deleteAdminUser,
        async (r) => r.current.mutateAsync(4),
      )
    })

    it('useCreateAdminUser', async () => {
      await expectInvalidateAfter(
        () => useCreateAdminUser(),
        adminFns.createAdminUser,
        async (r) => r.current.mutateAsync({ email: 'a@b.co', password: 'x' }),
      )
    })

    it('useChangeReportStatus invalidates reports', async () => {
      await expectInvalidateAfter(
        () => useChangeReportStatus(),
        adminFns.changeReportStatus,
        async (r) => r.current.mutateAsync({ id: 9, status: 'VERIFIED' }),
      )
    })

    it('useDeleteAdminReport', async () => {
      await expectInvalidateAfter(
        () => useDeleteAdminReport(),
        adminFns.deleteAdminReport,
        async (r) => r.current.mutateAsync(10),
      )
    })

    it('useDeleteAdminComment invalidates comments + stats', async () => {
      useAuth.mockReturnValue(auth)
      adminFns.deleteAdminComment.mockResolvedValue(undefined)
      const inv = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => useDeleteAdminComment(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync(11)
      })

      expect(adminFns.deleteAdminComment).toHaveBeenCalledWith(11, 'tok')
      expect(inv).toHaveBeenCalled()
    })

    it('useDeleteAdminValidation invalidates validations only', async () => {
      useAuth.mockReturnValue(auth)
      adminFns.deleteAdminValidation.mockResolvedValue(undefined)
      const inv = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => useDeleteAdminValidation(), {
        wrapper: makeWrapper(queryClient),
      })

      await act(async () => {
        await result.current.mutateAsync(12)
      })

      expect(adminFns.deleteAdminValidation).toHaveBeenCalledWith(12, 'tok')
      expect(inv).toHaveBeenCalled()
    })
  })
})
