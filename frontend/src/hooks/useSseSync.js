import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { connect } from '../services/sseClient.js'
import { reportKeys, applyReportUpdatedToCache } from './useReports.js'
import { useSseStatusSetter } from '../context/SseContext.jsx'

const SSE_URL = `${import.meta.env.VITE_API_URL}/api/sse/public/subscribe`

/**
 * Mount once at app root.
 * Opens the SSE connection, syncs cache on events, and exposes connection
 * status via SseContext so any component can read it via useSseStatus().
 */
export function useSseSync() {
  const queryClient = useQueryClient()
  const setStatus = useSseStatusSetter()

  useEffect(() => {
    const close = connect(SSE_URL, {
      onStatusChange: setStatus,

      onConnected() {
        // Nothing extra needed; status already set to 'connected' by sseClient
      },

      onReportUpdated(event) {
        // 1. Update the report in the list cache in-place (instant map marker update)
        applyReportUpdatedToCache(queryClient, event)

        // 2. Invalidate detail query so open detail panel refreshes in background
        queryClient.invalidateQueries({
          queryKey: reportKeys.detail(event.reportId),
        })
      },

      onMediaAdded(event) {
        // Invalidate detail so media list refreshes for the affected report
        queryClient.invalidateQueries({
          queryKey: reportKeys.detail(event.reportId),
        })
      },

      onReportCreated() {
        // Payload only contains basic fields (no lat/lng etc), so re-fetch the list
        queryClient.invalidateQueries({ queryKey: reportKeys.lists() })
      },

      onReportDeleted(event) {
        // Remove the deleted report from the list cache immediately
        queryClient.setQueryData(reportKeys.lists(), (prev) => {
          if (!prev) return prev
          return prev.filter((r) => r.id !== event.reportId)
        })
        // Also remove detail cache if it exists
        queryClient.removeQueries({ queryKey: reportKeys.detail(event.reportId) })
      },
    })

    return close
  }, [queryClient, setStatus])
}
