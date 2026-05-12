/**
 * Helpers for the GeoJSON RFC 7946 wire format used by the backend.
 *
 * The backend exposes coordinates as GeoJSON geometry objects:
 *   { type: "Point",      coordinates: [longitude, latitude] }
 *   { type: "LineString", coordinates: [[longitude, latitude], ...] }
 *
 * Leaflet, by contrast, uses [latitude, longitude] order everywhere — these
 * helpers exist so the order swap happens once at the API boundary instead
 * of being inlined throughout the map components.
 */

/** Build a GeoJSON Point from a (lat, lng) pair. */
export function geoJsonPoint(latitude, longitude) {
  const lat = Number(latitude)
  const lon = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null
  return { type: 'Point', coordinates: [lon, lat] }
}

/**
 * Extract a Leaflet-friendly { lat, lng } from a GeoJSON Point.
 * Returns null for missing/malformed geometry.
 */
export function latLngFromGeoJson(geometry) {
  if (!geometry || geometry.type !== 'Point') return null
  const [lon, lat] = geometry.coordinates || []
  if (!Number.isFinite(lon) || !Number.isFinite(lat)) return null
  return { lat, lng: lon }
}

/**
 * Pick coordinates from an API report, preferring the GeoJSON `geometry`
 * field and falling back to the legacy `latitude`/`longitude` scalars
 * for backwards compatibility while the migration is in flight.
 *
 * Returns null when neither shape carries usable coordinates — callers can
 * then render an "unavailable" placeholder. Note the explicit null/empty
 * guards on the scalar fallback: `Number(null)` is 0, so without them a
 * missing pair would silently render as the gulf of guinea.
 */
export function reportLatLng(report) {
  if (!report) return null
  const fromGeometry = latLngFromGeoJson(report.geometry)
  if (fromGeometry) return fromGeometry
  if (report.latitude == null || report.longitude == null) return null
  if (report.latitude === '' || report.longitude === '') return null
  const lat = Number(report.latitude)
  const lng = Number(report.longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  return { lat, lng }
}

/**
 * Convert a GeoJSON LineString into the [lat, lng] tuple array that Leaflet's
 * `Polyline` component expects.
 */
export function leafletPathFromLineString(lineString) {
  if (!lineString || lineString.type !== 'LineString') return []
  const coords = lineString.coordinates || []
  const path = []
  for (const pair of coords) {
    if (!Array.isArray(pair) || pair.length < 2) continue
    const [lon, lat] = pair
    if (Number.isFinite(lon) && Number.isFinite(lat)) path.push([lat, lon])
  }
  return path
}
