import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext.jsx'
import { updateLanguage } from '../services/userService.js'
import { currentUserKey } from './useCurrentUser.js'

/**
 * Switch the UI language locally AND persist the choice to the backend.
 *
 * For guests (no token) we still flip i18next so the UI changes immediately;
 * the choice doesn't survive a hard refresh until they log in (the browser
 * detector will pick whatever was last set).
 *
 * Backend errors don't block the UI change — if the persistence call fails
 * (e.g. endpoint not deployed yet) the user still gets the translated UI for
 * the rest of the session.
 */
export function useSetLanguage() {
  const { token, userId, isAuthenticated } = useAuth()
  const { i18n } = useTranslation()
  const queryClient = useQueryClient()

  return useMutation({
    // language = 'EN' | 'TR' (wire form matches the backend Language enum)
    mutationFn: async (language) => {
      const code = language.toUpperCase()
      // Flip the UI optimistically — react-i18next re-renders every consumer.
      await i18n.changeLanguage(code.toLowerCase())
      if (isAuthenticated && userId) {
        try {
          return await updateLanguage(userId, code, token)
        } catch (err) {
          // Swallow — UI already changed. Surface to console for debugging.
          console.warn('[useSetLanguage] backend persistence failed:', err.message)
          return null
        }
      }
      return null
    },
    onSuccess: () => {
      // Refresh the cached profile so any consumer reading preferredLanguage
      // (settings page, etc.) sees the new value.
      queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
  })
}
