import { describe, it, expect } from 'vitest'
import {
  geoJsonPoint,
  latLngFromGeoJson,
  reportLatLng,
  leafletPathFromLineString,
} from './geojson.js'

describe('geoJsonPoint', () => {
  it('returns GeoJSON Point with [lon, lat] order', () => {
    expect(geoJsonPoint(41.02, 29.01)).toEqual({
      type: 'Point',
      coordinates: [29.01, 41.02],
    })
  })

  it('returns null when latitude is not finite', () => {
    expect(geoJsonPoint(NaN, 1)).toBeNull()
    expect(geoJsonPoint('x', 1)).toBeNull()
  })

  it('returns null when longitude is not finite', () => {
    expect(geoJsonPoint(1, Infinity)).toBeNull()
  })
})

describe('latLngFromGeoJson', () => {
  it('maps Point coordinates to Leaflet lat/lng', () => {
    expect(
      latLngFromGeoJson({ type: 'Point', coordinates: [29, 41] }),
    ).toEqual({ lat: 41, lng: 29 })
  })

  it('returns null for wrong type or missing coords', () => {
    expect(latLngFromGeoJson(null)).toBeNull()
    expect(latLngFromGeoJson({ type: 'LineString', coordinates: [] })).toBeNull()
    expect(latLngFromGeoJson({ type: 'Point', coordinates: [NaN, 1] })).toBeNull()
  })
})

describe('reportLatLng', () => {
  it('prefers GeoJSON geometry over legacy scalars', () => {
    expect(
      reportLatLng({
        geometry: { type: 'Point', coordinates: [10, 20] },
        latitude: 99,
        longitude: 99,
      }),
    ).toEqual({ lat: 20, lng: 10 })
  })

  it('falls back to latitude/longitude when geometry missing', () => {
    expect(reportLatLng({ latitude: '41', longitude: '29' })).toEqual({ lat: 41, lng: 29 })
  })

  it('returns null when scalars are absent or empty string', () => {
    expect(reportLatLng(null)).toBeNull()
    expect(reportLatLng({ latitude: null, longitude: 0 })).toBeNull()
    expect(reportLatLng({ latitude: '', longitude: '1' })).toBeNull()
  })

  it('returns null when scalar pair is not numeric', () => {
    expect(reportLatLng({ latitude: 'x', longitude: 'y' })).toBeNull()
  })
})

describe('leafletPathFromLineString', () => {
  it('converts LineString to [lat,lng] tuples', () => {
    expect(
      leafletPathFromLineString({
        type: 'LineString',
        coordinates: [
          [10, 20],
          [11, 21],
        ],
      }),
    ).toEqual([
      [20, 10],
      [21, 11],
    ])
  })

  it('returns empty array for invalid input', () => {
    expect(leafletPathFromLineString(null)).toEqual([])
    expect(leafletPathFromLineString({ type: 'Point', coordinates: [] })).toEqual([])
  })

  it('skips malformed coordinate pairs', () => {
    expect(
      leafletPathFromLineString({
        type: 'LineString',
        coordinates: [[10, 20], [NaN, 20], [11]],
      }),
    ).toEqual([[20, 10]])
  })
})
