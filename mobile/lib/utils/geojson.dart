/// Helpers for the GeoJSON RFC 7946 wire format used by the backend.
///
/// Backend exposes coordinates as GeoJSON geometry objects:
///   {"type": "Point",      "coordinates": [longitude, latitude]}
///   {"type": "LineString", "coordinates": [[longitude, latitude], ...]}
///
/// `latlong2.LatLng` and `flutter_map` use `LatLng(lat, lng)` order — the
/// opposite of GeoJSON's `[lon, lat]`. These helpers centralize the swap so
/// the order flip happens once at the API boundary instead of inline in
/// every screen.
library;

/// Build a GeoJSON Point payload from a (lat, lon) pair.
/// Returns `null` when either coordinate is non-finite.
Map<String, dynamic>? geoJsonPoint(double latitude, double longitude) {
  if (latitude.isNaN || longitude.isNaN ||
      !latitude.isFinite || !longitude.isFinite) {
    return null;
  }
  return {
    'type': 'Point',
    'coordinates': [longitude, latitude],
  };
}

/// Pick coordinates from a report JSON map, preferring the GeoJSON `geometry`
/// field and falling back to the legacy scalar `latitude` / `longitude` for
/// older responses still in flight. Returns `null` when neither shape carries
/// usable coordinates — callers can then render a placeholder.
({double lat, double lon})? extractLatLon(Map<String, dynamic> json) {
  final geometry = json['geometry'];
  if (geometry is Map &&
      geometry['type'] == 'Point' &&
      geometry['coordinates'] is List) {
    final coords = geometry['coordinates'] as List;
    if (coords.length >= 2 &&
        coords[0] is num &&
        coords[1] is num) {
      return (lat: (coords[1] as num).toDouble(), lon: (coords[0] as num).toDouble());
    }
  }
  final lat = json['latitude'];
  final lon = json['longitude'];
  if (lat is num && lon is num) {
    return (lat: lat.toDouble(), lon: lon.toDouble());
  }
  return null;
}

/// Same pattern as [extractLatLon] but for the optional entry/exit point
/// fields on FEATURE (ramp) reports.
({double lat, double lon})? extractOptionalLatLon(
  Map<String, dynamic> json, {
  required String geometryKey,
  required String latKey,
  required String lonKey,
}) {
  final geometry = json[geometryKey];
  if (geometry is Map &&
      geometry['type'] == 'Point' &&
      geometry['coordinates'] is List) {
    final coords = geometry['coordinates'] as List;
    if (coords.length >= 2 &&
        coords[0] is num &&
        coords[1] is num) {
      return (lat: (coords[1] as num).toDouble(), lon: (coords[0] as num).toDouble());
    }
  }
  final lat = json[latKey];
  final lon = json[lonKey];
  if (lat is num && lon is num) {
    return (lat: lat.toDouble(), lon: lon.toDouble());
  }
  return null;
}
