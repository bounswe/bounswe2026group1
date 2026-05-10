import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { getLeaderboard } from '../services/leaderboardService.js'

export const leaderboardKey = (isAuthenticated) => ['leaderboard', isAuthenticated ? 'auth' : 'anon']

/**
 * Fetches the top-N leaderboard plus, when authenticated, the caller's rank.
 * The query key is segmented by auth state so anonymous and authenticated
 * sessions don't share each other's `callerRank` slot.
 */
export function useLeaderboard() {
  const { token, isAuthenticated } = useAuth()
  return useQuery({
    queryKey: leaderboardKey(isAuthenticated),
    queryFn: () => getLeaderboard(token),
    staleTime: 60_000,
  })
}
