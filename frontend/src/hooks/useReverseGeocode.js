import { useQuery } from '@tanstack/react-query'
import { reverseGeocode, reverseGeocodeKey } from '../services/reverseGeocodeService.js'

export function useReverseGeocode(latitude, longitude, { enabled = true } = {}) {
  const key = reverseGeocodeKey(latitude, longitude)
  return useQuery({
    queryKey: key ?? ['reverseGeocode', 'invalid'],
    queryFn: () => reverseGeocode(latitude, longitude),
    enabled: enabled && key != null,
    staleTime: 24 * 60 * 60 * 1000,
    gcTime: 24 * 60 * 60 * 1000,
    retry: 1,
  })
}
