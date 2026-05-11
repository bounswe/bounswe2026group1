import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useReports,
  useReport,
  useUpdateMapReport,
  useDeleteMapReport,
  applyReportUpdatedToCache,
  reportKeys,
} from './useReports.js'
import { mapReport } from '../services/reportService.js'

vi.mock('../context/SseContext.jsx', () => ({
  useSseStatus: vi.fn(),
}))
vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../services/reportService.js', async (importOriginal) => {
  const mod = await importOriginal()
  return {
    ...mod,
    getReports: vi.fn(),
    getReportById: vi.fn(),
    updateReport: vi.fn(),
    deleteReport: vi.fn(),
  }
})

import { useSseStatus } from '../context/SseContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { getReports, getReportById, updateReport, deleteReport } from '../services/reportService.js'

function makeWrapper(qc) {
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useReports module', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    useAuth.mockReturnValue({ token: 'tok' })
    useSseStatus.mockReturnValue('connected')
  })

  describe('useReports', () => {
    it('fetches and maps the report list', async () => {
      const raw = []
      getReports.mockResolvedValue(raw)

      const { result } = renderHook(() => useReports(), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getReports).toHaveBeenCalledWith('tok')
      expect(result.current.data).toEqual(raw.map(mapReport))
    })
  })

  describe('useReport', () => {
    it('does not fetch when id is null', () => {
      renderHook(() => useReport(null), { wrapper: makeWrapper(queryClient) })
      expect(getReportById).not.toHaveBeenCalled()
    })

    it('fetches single report by id', async () => {
      const raw = {
        reportId: 5,
        userId: 9,
        reportType: 'OBSTACLE',
        objects: [],
        latitude: 1,
        longitude: 2,
        description: 'd',
        status: 'PENDING',
        agrees: 0,
        disagrees: 0,
      }
      getReportById.mockResolvedValue(raw)

      const { result } = renderHook(() => useReport(5), { wrapper: makeWrapper(queryClient) })

      await waitFor(() => expect(result.current.isSuccess).toBe(true))
      expect(getReportById).toHaveBeenCalledWith(5, 'tok')
      expect(result.current.data).toEqual(mapReport(raw))
    })
  })

  describe('useUpdateMapReport / useDeleteMapReport', () => {
    it('update calls API', async () => {
      updateReport.mockResolvedValue({})
      const { result } = renderHook(() => useUpdateMapReport(), { wrapper: makeWrapper(queryClient) })
      await act(async () => {
        await result.current.mutateAsync({ id: 1, body: { description: 'x' } })
      })
      expect(updateReport).toHaveBeenCalledWith(1, { description: 'x' }, 'tok')
    })

    it('delete calls API', async () => {
      deleteReport.mockResolvedValue(undefined)
      const { result } = renderHook(() => useDeleteMapReport(), { wrapper: makeWrapper(queryClient) })
      await act(async () => {
        await result.current.mutateAsync(9)
      })
      expect(deleteReport).toHaveBeenCalledWith(9, 'tok')
    })
  })

  describe('applyReportUpdatedToCache', () => {
    it('merges counts and status into the list cache', () => {
      const qc = new QueryClient()
      const list = [
        { id: 1, agrees: 0, disagrees: 0, status: 'unverified' },
        { id: 2, agrees: 1, disagrees: 0, status: 'verified' },
      ]
      qc.setQueryData(reportKeys.lists(), list)

      applyReportUpdatedToCache(qc, {
        reportId: 1,
        agrees: 5,
        disagrees: 1,
        status: 'VERIFIED',
      })

      const next = qc.getQueryData(reportKeys.lists())
      expect(next[0]).toMatchObject({ id: 1, agrees: 5, disagrees: 1, status: 'verified' })
      expect(next[1]).toEqual(list[1])
    })

    it('no-ops when list cache is missing', () => {
      const qc = new QueryClient()
      applyReportUpdatedToCache(qc, { reportId: 1, agrees: 1, disagrees: 0, status: 'PENDING' })
      expect(qc.getQueryData(reportKeys.lists())).toBeUndefined()
    })
  })
})
