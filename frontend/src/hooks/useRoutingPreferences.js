import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  activateCustomProfile,
  createCustomProfile,
  deleteCustomProfile,
  getRoutingPreferences,
  updateCustomProfile,
  updateRoutingPreferences,
} from '../services/routingPreferencesService.js'

export const routingPreferencesKey = ['routingPreferences']

export function useRoutingPreferences() {
  const { token, isAuthenticated } = useAuth()
  return useQuery({
    queryKey: routingPreferencesKey,
    queryFn: () => getRoutingPreferences(token),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })
}

function useInvalidatePreferences() {
  const queryClient = useQueryClient()
  return (data) => {
    if (data) queryClient.setQueryData(routingPreferencesKey, data)
    queryClient.invalidateQueries({ queryKey: routingPreferencesKey })
  }
}

export function useUpdateRoutingPreferences() {
  const { token } = useAuth()
  const refresh = useInvalidatePreferences()
  return useMutation({
    mutationFn: (body) => updateRoutingPreferences(body, token),
    onSuccess: refresh,
  })
}

export function useCreateCustomProfile() {
  const { token } = useAuth()
  const refresh = useInvalidatePreferences()
  return useMutation({
    mutationFn: (body) => createCustomProfile(body, token),
    // Profile list lives inside the routing-preferences response; refetch
    // to pick up the new entry without merging by hand.
    onSuccess: () => refresh(),
  })
}

export function useUpdateCustomProfile() {
  const { token } = useAuth()
  const refresh = useInvalidatePreferences()
  return useMutation({
    mutationFn: ({ id, body }) => updateCustomProfile(id, body, token),
    onSuccess: () => refresh(),
  })
}

export function useDeleteCustomProfile() {
  const { token } = useAuth()
  const refresh = useInvalidatePreferences()
  return useMutation({
    mutationFn: (id) => deleteCustomProfile(id, token),
    onSuccess: () => refresh(),
  })
}

export function useActivateCustomProfile() {
  const { token } = useAuth()
  const refresh = useInvalidatePreferences()
  return useMutation({
    mutationFn: (id) => activateCustomProfile(id, token),
    onSuccess: refresh,
  })
}
