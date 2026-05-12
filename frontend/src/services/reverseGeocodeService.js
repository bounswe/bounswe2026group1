// Resolve a coordinate pair to a short place label via Nominatim. Used by the
// feed cards and other surfaces when the backend did not store a reverse-
// geocoded label at create time (older rows / external imports).

export async function reverseGeocode(latitude, longitude) {
  if (latitude == null || longitude == null) return null
  const lat = Number(latitude)
  const lon = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null

  const res = await fetch(
    `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json`
  )
  if (!res.ok) throw new Error(`Nominatim ${res.status}`)
  const data = await res.json()
  const parts = data.display_name?.split(',') ?? []
  const label = parts.slice(0, 2).join(',').trim()
  return label || null
}

export function reverseGeocodeKey(latitude, longitude) {
  const lat = Number(latitude)
  const lon = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null
  // ~11m precision; coarse enough to dedupe nearby reports.
  return ['reverseGeocode', lat.toFixed(4), lon.toFixed(4)]
}
