import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { searchUsers } from '../services/userService.js'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 300

function useDebouncedValue(value, delay) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}

export function useUserSearch(query, { page = 0, size = 20 } = {}) {
  const { token, isAuthenticated } = useAuth()
  const debouncedQuery = useDebouncedValue(query.trim(), DEBOUNCE_MS)
  const enabled = isAuthenticated && debouncedQuery.length >= MIN_QUERY_LENGTH

  return useQuery({
    queryKey: ['userSearch', debouncedQuery, page, size],
    queryFn: () => searchUsers(debouncedQuery, { page, size }, token),
    enabled,
    keepPreviousData: true,
    staleTime: 30_000,
  })
}

export const USER_SEARCH_MIN_LENGTH = MIN_QUERY_LENGTH
