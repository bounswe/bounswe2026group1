import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  banUser,
  changeReportStatus,
  changeUserRole,
  createAdminUser,
  deleteAdminComment,
  deleteAdminReport,
  deleteAdminUser,
  deleteAdminValidation,
  getAdminComments,
  getAdminReports,
  getAdminStats,
  getAdminUser,
  getAdminUsers,
  getAdminValidations,
  unbanUser,
} from '../services/adminService.js'

export const adminKeys = {
  all: ['admin'],
  stats: () => [...adminKeys.all, 'stats'],
  users: (filters) => [...adminKeys.all, 'users', filters ?? {}],
  user: (id) => [...adminKeys.all, 'user', id],
  reports: (filters) => [...adminKeys.all, 'reports', filters ?? {}],
  comments: (filters) => [...adminKeys.all, 'comments', filters ?? {}],
  validations: (filters) => [...adminKeys.all, 'validations', filters ?? {}],
}

/* -------------------------------------------------------------------------- */
/* Queries                                                                     */
/* -------------------------------------------------------------------------- */

export function useAdminStats() {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.stats(),
    queryFn: () => getAdminStats(token),
    enabled: !!token && isAdmin,
    staleTime: 30_000,
  })
}

export function useAdminUsersQuery(filters) {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.users(filters),
    queryFn: () => getAdminUsers(filters, token),
    enabled: !!token && isAdmin,
    keepPreviousData: true,
  })
}

export function useAdminUser(id) {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.user(id),
    queryFn: () => getAdminUser(id, token),
    enabled: !!token && isAdmin && id != null,
  })
}

export function useAdminReportsQuery(filters) {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.reports(filters),
    queryFn: () => getAdminReports(filters, token),
    enabled: !!token && isAdmin,
    keepPreviousData: true,
  })
}

export function useAdminCommentsQuery(filters) {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.comments(filters),
    queryFn: () => getAdminComments(filters, token),
    enabled: !!token && isAdmin,
    keepPreviousData: true,
  })
}

export function useAdminValidationsQuery(filters) {
  const { token, isAdmin } = useAuth()
  return useQuery({
    queryKey: adminKeys.validations(filters),
    queryFn: () => getAdminValidations(filters, token),
    enabled: !!token && isAdmin,
    keepPreviousData: true,
  })
}

/* -------------------------------------------------------------------------- */
/* Mutations                                                                   */
/* -------------------------------------------------------------------------- */

function invalidateUsers(queryClient) {
  queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'users'] })
  queryClient.invalidateQueries({ queryKey: adminKeys.stats() })
}

function invalidateReports(queryClient) {
  queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'reports'] })
  queryClient.invalidateQueries({ queryKey: adminKeys.stats() })
}

export function useBanUser() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => banUser(id, token),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useUnbanUser() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => unbanUser(id, token),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useChangeUserRole() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }) => changeUserRole(id, role, token),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useDeleteAdminUser() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => deleteAdminUser(id, token),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useCreateAdminUser() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => createAdminUser(payload, token),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useChangeReportStatus() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }) => changeReportStatus(id, status, token),
    onSuccess: () => invalidateReports(queryClient),
  })
}

export function useDeleteAdminReport() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => deleteAdminReport(id, token),
    onSuccess: () => invalidateReports(queryClient),
  })
}

export function useDeleteAdminComment() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => deleteAdminComment(id, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'comments'] })
      queryClient.invalidateQueries({ queryKey: adminKeys.stats() })
    },
  })
}

export function useDeleteAdminValidation() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => deleteAdminValidation(id, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'validations'] })
    },
  })
}
