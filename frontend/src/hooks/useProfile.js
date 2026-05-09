import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import {
  deleteAvatar as deleteAvatarRequest,
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
