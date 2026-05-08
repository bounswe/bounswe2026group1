import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  deleteReport as deleteReportRequest,
  getReportsByUserId,
  updateReport as updateReportRequest,
} from '../services/reportService.js'
import { currentUserKey } from './useCurrentUser.js'

export const userReportsKey = (userId) => ['userReports', userId]

export function useUserReports(userId) {
  return useQuery({
    queryKey: userReportsKey(userId),
    queryFn: () => getReportsByUserId(userId),
    enabled: !!userId,
  })
}

export function useUpdateReport(userId) {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }) => updateReportRequest(id, body, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userReportsKey(userId) })
    },
  })
}

export function useDeleteReport(userId) {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => deleteReportRequest(id, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userReportsKey(userId) })
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}
