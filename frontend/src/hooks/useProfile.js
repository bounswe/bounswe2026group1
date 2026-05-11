import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  deleteAvatar as deleteAvatarRequest,
  deleteCurrentUser as deleteCurrentUserRequest,
  updateProfile as updateProfileRequest,
  uploadAvatar as uploadAvatarRequest,
} from '../services/userService.js'
import { currentUserKey } from './useCurrentUser.js'

export function useUpdateProfile() {
  const { token, userId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body) => updateProfileRequest(userId, body, token),
    onSuccess: (data) => {
      if (data) queryClient.setQueryData(currentUserKey, data)
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}

export function useUploadAvatar() {
  const { token, userId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file) => uploadAvatarRequest(userId, file, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}

export function useDeleteAvatar() {
  const { token, userId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteAvatarRequest(userId, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}

// Self-delete (1.1.1.2.12). The caller's row is anonymized server-side;
// we clear the caller's session here so they're logged out client-side.
// Does not invalidate any other cache — the session goes away and most
// queries refetch as guest on the next mount.
export function useDeleteAccount() {
  const { token, logout } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteCurrentUserRequest(token),
    onSuccess: () => {
      queryClient.clear()
      logout()
    },
  })
}
