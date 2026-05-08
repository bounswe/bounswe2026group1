import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { getCurrentUser } from '../services/userService.js'

export const currentUserKey = ['currentUser']

export function useCurrentUser() {
  const { token, isAuthenticated } = useAuth()
  return useQuery({
    queryKey: currentUserKey,
    queryFn: () => getCurrentUser(token),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })
}
