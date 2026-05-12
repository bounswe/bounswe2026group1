import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/utils/geojson.dart';

void main() {
  group('geoJsonPoint', () {
    test('returns correct GeoJSON for valid coordinates', () {
      final result = geoJsonPoint(41.0, 29.0);
      expect(result, isNotNull);
      expect(result!['type'], 'Point');
      expect(result['coordinates'], [29.0, 41.0]); // [lon, lat]
    });

    test('returns null for NaN latitude', () {
      expect(geoJsonPoint(double.nan, 29.0), isNull);
    });

    test('returns null for NaN longitude', () {
      expect(geoJsonPoint(41.0, double.nan), isNull);
    });

    test('returns null for infinite latitude', () {
      expect(geoJsonPoint(double.infinity, 29.0), isNull);
    });

    test('returns null for infinite longitude', () {
      expect(geoJsonPoint(41.0, double.negativeInfinity), isNull);
    });

    test('works with zero coordinates', () {
      final result = geoJsonPoint(0.0, 0.0);
      expect(result, isNotNull);
      expect(result!['coordinates'], [0.0, 0.0]);
    });

    test('works with negative coordinates', () {
      final result = geoJsonPoint(-33.9, -70.7);
      expect(result, isNotNull);
      expect(result!['coordinates'], [-70.7, -33.9]);
    });
  });

  group('extractLatLon', () {
    test('extracts from GeoJSON geometry', () {
      final result = extractLatLon({
        'geometry': {
          'type': 'Point',
          'coordinates': [29.0, 41.0],
        },
      });
      expect(result, isNotNull);
      expect(result!.lat, 41.0);
      expect(result.lon, 29.0);
    });

    test('falls back to legacy lat/lon scalars', () {
      final result = extractLatLon({'latitude': 41.0, 'longitude': 29.0});
      expect(result, isNotNull);
      expect(result!.lat, 41.0);
      expect(result.lon, 29.0);
    });

    test('returns null when neither geometry nor scalars present', () {
      expect(extractLatLon({}), isNull);
    });

    test('returns null when geometry coordinates list is too short', () {
      final result = extractLatLon({
        'geometry': {'type': 'Point', 'coordinates': [29.0]},
      });
      expect(result, isNull);
    });

    test('returns null when geometry type is not Point', () {
      final result = extractLatLon({
        'geometry': {
          'type': 'LineString',
          'coordinates': [[29.0, 41.0]],
        },
      });
      expect(result, isNull);
    });

    test('prefers geometry over legacy scalars when both present', () {
      final result = extractLatLon({
        'geometry': {
          'type': 'Point',
          'coordinates': [10.0, 20.0],
        },
        'latitude': 99.0,
        'longitude': 99.0,
      });
      expect(result!.lat, 20.0);
      expect(result.lon, 10.0);
    });
  });

  group('extractOptionalLatLon', () {
    test('extracts from geometry key', () {
      final result = extractOptionalLatLon(
        {
          'entryGeometry': {
            'type': 'Point',
            'coordinates': [29.0, 41.0],
          },
        },
        geometryKey: 'entryGeometry',
        latKey: 'entryLat',
        lonKey: 'entryLon',
      );
      expect(result, isNotNull);
      expect(result!.lat, 41.0);
      expect(result.lon, 29.0);
    });

    test('falls back to named lat/lon keys', () {
      final result = extractOptionalLatLon(
        {'entryLat': 41.0, 'entryLon': 29.0},
        geometryKey: 'entryGeometry',
        latKey: 'entryLat',
        lonKey: 'entryLon',
      );
      expect(result, isNotNull);
      expect(result!.lat, 41.0);
      expect(result.lon, 29.0);
    });

    test('returns null when nothing present', () {
      final result = extractOptionalLatLon(
        {},
        geometryKey: 'entryGeometry',
        latKey: 'entryLat',
        lonKey: 'entryLon',
      );
      expect(result, isNull);
    });
  });
}
