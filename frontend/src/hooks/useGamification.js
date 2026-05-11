import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  getBadges as getBadgesRequest,
  setLeaderboardVisibility as setLeaderboardVisibilityRequest,
  setVoiceCommandsEnabled as setVoiceCommandsEnabledRequest,
} from '../services/userService.js'
import { currentUserKey } from './useCurrentUser.js'

export const userBadgesKey = (userId) => ['userBadges', userId]

/** Fetches the public list of badges held by a user. No auth required —
 *  the badges endpoint is public so anonymous visitors of a profile can
 *  see them. */
export function useUserBadges(userId) {
  return useQuery({
    queryKey: userBadgesKey(userId),
    queryFn: () => getBadgesRequest(userId),
    enabled: !!userId,
    staleTime: 60_000,
  })
}

/** Toggles the caller's leaderboard opt-out flag. On success the cached
 *  current-user profile is invalidated so the rank/leaderboardHidden flag
 *  in the profile DTO refreshes immediately. */
export function useSetLeaderboardVisibility() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (leaderboardHidden) =>
      setLeaderboardVisibilityRequest(token, leaderboardHidden),
    onSuccess: (data) => {
      if (data) queryClient.setQueryData(currentUserKey, data)
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}

/** Toggles the caller's voice-command accessibility flag (1.1.3.8).
 *  Updates the cached current-user profile so the Navbar mic toggle reflects
 *  the new state without a refetch round-trip. */
export function useSetVoiceCommandsEnabled() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (voiceCommandsEnabled) =>
      setVoiceCommandsEnabledRequest(token, voiceCommandsEnabled),
    onSuccess: (data) => {
      if (data) queryClient.setQueryData(currentUserKey, data)
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}
