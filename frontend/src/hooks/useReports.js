import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getReports, getReportById, mapReport } from '../services/reportService.js'
import { useSseStatus } from '../context/SseContext.jsx'

export const reportKeys = {
  all: ['reports'],
  lists: () => [...reportKeys.all, 'list'],
  detail: (id) => [...reportKeys.all, 'detail', id],
}

/**
 * Fetch the full report list.
 * Falls back to polling every 30 s when SSE is disconnected.
 */
export function useReports() {
  const sseStatus = useSseStatus()
  const disconnected = sseStatus !== 'connected'

  return useQuery({
    queryKey: reportKeys.lists(),
    queryFn: () => getReports().then((data) => data.map(mapReport)),
    staleTime: 30_000,
    refetchInterval: disconnected ? 30_000 : false,
  })
}

/**
 * Fetch a single report by numeric ID.
 * Falls back to polling every 30 s when SSE is disconnected.
 */
export function useReport(id) {
  const sseStatus = useSseStatus()
  const disconnected = sseStatus !== 'connected'

  return useQuery({
    queryKey: reportKeys.detail(id),
    queryFn: () => getReportById(id).then(mapReport),
    enabled: id != null,
    staleTime: 30_000,
    refetchInterval: disconnected ? 30_000 : false,
  })
}

/**
 * Apply a report-updated SSE event to the list cache in-place.
 * Call this from useSseSync instead of a full invalidation for the list,
 * so map markers update without a network round-trip.
 */
export function applyReportUpdatedToCache(queryClient, event) {
  const { reportId, agrees, disagrees, status } = event

  queryClient.setQueryData(reportKeys.lists(), (prev) => {
    if (!prev) return prev
    return prev.map((r) =>
      r.id === reportId
        ? {
            ...r,
            agrees,
            disagrees,
            status: status === 'VERIFIED' ? 'verified' : 'unverified',
          }
        : r
    )
  })
}
