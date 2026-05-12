import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useUserReports,
  useUpdateReport,
  useDeleteReport,
} from './useUserReports.js'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/reportService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return {
    ...mod,
    getReportsByUserId: vi.fn(),
    updateReport: vi.fn(),
    deleteReport: vi.fn(),
  }
})

import { useAuth } from '../context/AuthContext.jsx'
import {
  getReportsByUserId,
  updateReport,
  deleteReport,
  mapReport,
} from '../services/reportService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useUserReports hooks', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { mutations: { retry: false } } } })
    useAuth.mockReturnValue({ token: 'tok', userId: 1 })
  })

  describe('useUserReports', () => {
    it('does not fetch when userId is falsy', () => {
      renderHook(() => useUserReports(null), { wrapper: makeWrapper(queryClient) })
      expect(getReportsByUserId).not.toHaveBeenCalled()
    })

    it('maps reports from API', async () => {
      const raw = [
        {
          reportId: 10,
          userId: 1,
          reportType: 'OBSTACLE',
          objects: [],
          latitude: 0,
          longitude: 0,
          description: 'x',
          status: 'PENDING',
          agrees: 0,
          disagrees: 0,
        },
      ]
      getReportsByUserId.mockResolvedValue(raw)
      const mapped = raw.map(mapReport)

      const { result } = renderHook(() => useUserReports(99), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getReportsByUserId).toHaveBeenCalledWith(99)
      expect(result.current.data).toEqual(mapped)
    })
  })

  describe('useUpdateReport', () => {
    it('calls updateReport', async () => {
      updateReport.mockResolvedValue({})
      const { result } = renderHook(() => useUpdateReport(7), { wrapper: makeWrapper(queryClient) })

      await act(async () => {
        await result.current.mutateAsync({ id: 3, body: { title: 'x' } })
      })

      expect(updateReport).toHaveBeenCalledWith(3, { title: 'x' }, 'tok')
    })
  })

  describe('useDeleteReport', () => {
    it('calls deleteReport', async () => {
      deleteReport.mockResolvedValue(undefined)
      const { result } = renderHook(() => useDeleteReport(7), { wrapper: makeWrapper(queryClient) })

      await act(async () => {
        await result.current.mutateAsync(42)
      })

      expect(deleteReport).toHaveBeenCalledWith(42, 'tok')
    })
  })
})
