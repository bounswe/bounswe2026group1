import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { getUserById } from '../services/userService.js'

export function useUserProfile(userId) {
  const { token, isAuthenticated } = useAuth()
  return useQuery({
    queryKey: ['userProfile', userId],
    queryFn: () => getUserById(userId, token),
    enabled: isAuthenticated && !!userId,
    staleTime: 60_000,
  })
}
