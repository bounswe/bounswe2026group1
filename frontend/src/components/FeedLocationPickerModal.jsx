import { useEffect, useLayoutEffect, useRef, useState, useCallback } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { useTranslation } from 'react-i18next'
import { feedFilterChipClass } from '../utils/feedFilterChip.js'
import MapSearchBar from './MapSearchBar.jsx'

const pinIcon = L.divIcon({
  className: '',
  html: `<div style="
    width:36px;height:36px;
    background:#176a21;
    border-radius:50% 50% 50% 0;
    transform:rotate(-45deg);
    border:3px solid white;
    box-shadow:0 4px 12px rgba(0,0,0,0.2);
  "></div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 36],
})

function MapInvalidate({ active }) {
  const map = useMap()
  useEffect(() => {
    if (!active) return
    const t = requestAnimationFrame(() => {
      map.invalidateSize()
    })
    return () => cancelAnimationFrame(t)
  }, [map, active])
  return null
}

function MapClickPick({ onPick }) {
  useMapEvents({
    click(e) {
      onPick(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

/** Move map to a location chosen via search (not used for plain map clicks). */
function FlyToSearchResult({ target }) {
  const map = useMap()
  useEffect(() => {
    if (!target) return
    map.setView([target.lat, target.lng], Math.max(map.getZoom(), 14), { animate: true })
  }, [map, target])
  return null
}

/**
 * Full-screen map modal to pick the feed reference point. Uses the same floating
 * search bar as the Home map (`MapSearchBar`).
 */
export default function FeedLocationPickerModal({
  open,
  onDismiss,
  center,
  radiusKm,
  tileUrl,
  tileAttr,
  onFeedCenterChange,
  onLocateUnavailable,
}) {
  const { t } = useTranslation()
  const wasOpenRef = useRef(false)
  const [bootCenter, setBootCenter] = useState(null)
  const [pin, setPin] = useState({
    lat: center?.lat ?? 41.0683,
    lng: center?.lng ?? 29.0505,
  })
  const [flyTarget, setFlyTarget] = useState(null)
  const [searchBarKey, setSearchBarKey] = useState(0)

  useLayoutEffect(() => {
    if (open) {
      if (!wasOpenRef.current) {
        const c = {
          lat: center?.lat ?? 41.0683,
          lng: center?.lng ?? 29.0505,
        }
        setBootCenter(c)
        setPin(c)
        setFlyTarget(null)
        setSearchBarKey((k) => k + 1)
      }
      wasOpenRef.current = true
    } else {
      wasOpenRef.current = false
      setBootCenter(null)
      setFlyTarget(null)
    }
  }, [open, center])

  useEffect(() => {
    if (!open) return
    function onKeyDown(e) {
      if (e.key === 'Escape') onDismiss()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, onDismiss])

  const applyLocation = useCallback(
    (lat, lng, shouldFly = false) => {
      setPin({ lat, lng })
      onFeedCenterChange({ lat, lng })
      if (shouldFly) setFlyTarget({ lat, lng, key: Date.now() })
    },
    [onFeedCenterChange]
  )

  const handleMapClick = useCallback(
    (lat, lng) => {
      setPin({ lat, lng })
      onFeedCenterChange({ lat, lng })
    },
    [onFeedCenterChange]
  )

  function handleUseMyLocation() {
    if (!navigator.geolocation) {
      onLocateUnavailable?.()
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude
        const lng = pos.coords.longitude
        applyLocation(lat, lng, true)
      },
      () => onLocateUnavailable?.(),
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 60_000 }
    )
  }

  if (!open || !bootCenter) return null

  const radiusM = Math.max(0, Number(radiusKm) || 0) * 1000

  return (
    <div
      className="fixed inset-0 z-[2000] flex flex-col bg-background"
      role="dialog"
      aria-modal="true"
      aria-label={t('feed.pickerAriaLabel')}
    >
      <header className="flex shrink-0 items-center justify-between gap-3 px-4 py-3 border-b border-outline-variant/30 bg-surface-container-lowest/95 backdrop-blur-md">
        <button
          type="button"
          onClick={onDismiss}
          className="px-4 py-2 rounded-xl font-semibold text-on-surface hover:bg-surface-container-high transition-colors"
        >
          {t('feed.pickerCancel')}
        </button>
        <p className="text-sm text-on-surface-variant text-center flex-1 hidden sm:block">
          {t('feed.pickerHelp')}
        </p>
        <button
          type="button"
          onClick={onDismiss}
          className="px-5 py-2 rounded-xl font-semibold bg-primary text-on-primary shadow-sm hover:opacity-95 transition-opacity"
        >
          {t('feed.pickerDone')}
        </button>
      </header>

      <p className="sm:hidden px-4 py-2 text-xs text-on-surface-variant border-b border-outline-variant/20">
        {t('feed.pickerHelp')}
      </p>

      <div className="flex-1 relative min-h-0">
        <MapContainer
          center={[bootCenter.lat, bootCenter.lng]}
          zoom={14}
          zoomControl
          className="w-full h-full z-0"
          style={{ height: '100%', width: '100%' }}
        >
          <MapInvalidate active={open} />
          <FlyToSearchResult target={flyTarget} />
          <MapClickPick onPick={handleMapClick} />
          <TileLayer url={tileUrl} attribution={tileAttr} />
          <Circle
            center={[pin.lat, pin.lng]}
            radius={radiusM}
            pathOptions={{
              color: '#176a21',
              weight: 2,
              fillColor: '#176a21',
              fillOpacity: 0.12,
            }}
          />
          <Marker position={[pin.lat, pin.lng]} icon={pinIcon} />
        </MapContainer>

        <MapSearchBar
          key={searchBarKey}
          mapCenter={{ lat: pin.lat, lng: pin.lng }}
          initialValue=""
          inputId="feed-map-search-location"
          onLocationPicked={({ lat, lon }) => applyLocation(lat, lon, true)}
        />

        <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[1000] flex flex-col items-center gap-2 pointer-events-auto max-w-[calc(100%-2rem)]">
          <button
            type="button"
            onClick={handleUseMyLocation}
            className={`${feedFilterChipClass(false)} gap-2`}
          >
            <span className="material-symbols-outlined text-lg" aria-hidden>
              my_location
            </span>
            {t('feed.pickerUseMyLocation')}
          </button>
        </div>
      </div>
    </div>
  )
}
