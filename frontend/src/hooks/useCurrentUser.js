import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext.jsx'
import { getCurrentUser } from '../services/userService.js'
import { SUPPORTED_LANGUAGES } from '../i18n/index.js'

export const currentUserKey = ['currentUser']

export function useCurrentUser() {
  const { token, isAuthenticated } = useAuth()
  const { i18n } = useTranslation()
  const query = useQuery({
    queryKey: currentUserKey,
    queryFn: () => getCurrentUser(token),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })

  // Restore the user's persisted UI language once the profile loads. The
  // browser detector handles guests; this overrides for authenticated users
  // so a TR choice on one device follows them to another.
  useEffect(() => {
    const lang = query.data?.preferredLanguage?.toLowerCase()
    if (lang && SUPPORTED_LANGUAGES.includes(lang) && i18n.resolvedLanguage !== lang) {
      i18n.changeLanguage(lang)
    }
  }, [query.data?.preferredLanguage, i18n])

  return query
}
