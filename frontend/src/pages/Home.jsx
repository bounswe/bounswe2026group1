import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { MapContainer, TileLayer, Marker, Polyline, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { useQueryClient } from '@tanstack/react-query'
import Navbar from '../components/Navbar.jsx'
import ReportPanel from '../components/ReportPanel.jsx'
import CreateReportPanel from '../components/CreateReportPanel.jsx'
import RoutePanel from '../components/RoutePanel.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import Toast from '../components/Toast.jsx'
import MapSearchBar from '../components/MapSearchBar.jsx'
import OnboardingTutorial from '../components/OnboardingTutorial.jsx'
import { useReports, reportKeys } from '../hooks/useReports.js'
import { currentUserKey } from '../hooks/useCurrentUser.js'
import MapFilters from '../components/MapFilters.jsx'
import {
  parseExcluded,
  serializeExcluded,
  isReportVisible,
  excludedCount,
} from '../utils/mapFilters.js'
import { useTheme } from '../context/ThemeContext.jsx'

// First-visit onboarding flag. Cleared once the user dismisses or completes the
// tour. Visit `/?tutorial=1` (e.g. from the Navbar "Replay tutorial" entry) to
// force the modal to show again.
const ONBOARDING_FLAG = 'mapcess_onboarding_v1'

function shouldShowOnboarding(searchParams) {
  if (searchParams.get('tutorial') === '1') return true
  try {
    return typeof window !== 'undefined' && window.localStorage.getItem(ONBOARDING_FLAG) !== 'done'
  } catch {
    return false
  }
}

function decodePolyline(encoded) {
  const coords = []
  let index = 0, lat = 0, lng = 0
  while (index < encoded.length) {
    let b, shift = 0, result = 0
    do { b = encoded.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5 } while (b >= 0x20)
    lat += result & 1 ? ~(result >> 1) : result >> 1
    shift = 0; result = 0
    do { b = encoded.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5 } while (b >= 0x20)
    lng += result & 1 ? ~(result >> 1) : result >> 1
    coords.push([lat / 1e5, lng / 1e5])
  }
  return coords
}

// Map marker colour rule (issue #362):
//   FEATURE  reports = positive   → green
//   OBSTACLE reports = negative   → red
// Verified pins render at full opacity; unverified pins are visually faded
// so the eye is drawn to confirmed reports first. Selected pins always
// render at full opacity regardless so the user sees what they clicked.
const MARKER_GREEN = '#2E7D32'
const MARKER_RED   = '#C62828'
const MARKER_NEUTRAL = '#767777'
const MARKER_ICON_FALLBACK = 'warning'

function makeMarkerIcon(status, objectType, reportType, selected = false) {
  const cfg = OBJECT_TYPE_MAP[objectType] ?? { icon: MARKER_ICON_FALLBACK, markerColor: MARKER_NEUTRAL }
  const borderColor = reportType === 'FEATURE' ? MARKER_GREEN : MARKER_RED
  const isVerified = status === 'verified'
  const opacity = !isVerified && !selected ? 0.55 : 1
  const size = selected ? 56 : 40
  const iconFontSize = selected ? 28 : 20
  const borderWidth = selected ? 3.5 : 2.5
  // Outer halo on selected: a soft colored glow that draws the eye
  // without obscuring the icon. Built with a layered box-shadow.
  const shadow = selected
    ? `0 0 0 4px ${borderColor}33, 0 0 0 8px ${borderColor}1a, 0 6px 18px rgba(0,0,0,0.25)`
    : `0 4px 12px rgba(0,0,0,0.15)`
  return L.divIcon({
    className: selected ? 'mapcess-marker-selected' : '',
    html: `
      <div style="
        width:${size}px;height:${size}px;
        background:white;
        border-radius:50%;
        border:${borderWidth}px solid ${borderColor};
        box-shadow:${shadow};
        display:flex;align-items:center;justify-content:center;
        cursor:pointer;
        opacity:${opacity};
        transition:width 150ms ease, height 150ms ease, opacity 150ms ease;
      ">
        <span class="material-symbols-outlined" style="
          font-size:${iconFontSize}px;
          color:${borderColor};
          font-variation-settings:'FILL' 1;
          line-height:1;
        ">${cfg.icon}</span>
      </div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size * 0.6],
  })
}

function ZoomControls() {
  const map = useMap()
  const { t } = useTranslation()
  return (
    <div className="absolute right-3 sm:right-10 top-24 sm:top-1/3 sm:-translate-y-1/2 flex flex-col gap-2 z-[1000]">
      <button
        onClick={() => map.zoomIn()}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-surface-container-lowest/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-container-lowest text-secondary hover:text-primary transition-colors"
        aria-label={t('home.zoomIn')}
      >
        <span className="material-symbols-outlined">add</span>
      </button>
      <button
        onClick={() => map.zoomOut()}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-surface-container-lowest/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-container-lowest text-secondary hover:text-primary transition-colors"
        aria-label={t('home.zoomOut')}
      >
        <span className="material-symbols-outlined">remove</span>
      </button>
      <button
        onClick={() => map.locate({ setView: true, maxZoom: 16 })}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-surface-container-lowest/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-container-lowest text-secondary hover:text-primary transition-colors mt-2"
        aria-label={t('home.myLocation')}
      >
        <span className="material-symbols-outlined">my_location</span>
      </button>
    </div>
  )
}

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

function MapClickHandler({ active, onPick }) {
  useMapEvents({
    click(e) {
      if (active) onPick(e.latlng)
    },
  })
  return null
}

function RouteClickHandler({ onRightClick }) {
  useMapEvents({
    contextmenu(e) {
      L.DomEvent.preventDefault(e.originalEvent)
      onRightClick(e.latlng)
    },
  })
  return null
}

function MapCenterTracker({ onCenterChange }) {
  useMapEvents({
    moveend(e) {
      const { lat, lng } = e.target.getCenter()
      onCenterChange({ lat, lng })
    },
    locationfound(e) {
      onCenterChange({ lat: e.latlng.lat, lng: e.latlng.lng })
    },
  })
  return null
}

const SESSION_MAP_VIEW_KEY = 'home_map_view'

function MapViewPersist() {
  useMapEvents({
    moveend(e) {
      const { lat, lng } = e.target.getCenter()
      const zoom = e.target.getZoom()
      try {
        sessionStorage.setItem(SESSION_MAP_VIEW_KEY, JSON.stringify({ lat, lng, zoom }))
      } catch {}
    },
  })
  return null
}

function GeolocateOnLoad({ onLocation, autoPan = true }) {
  const map = useMap()
  useEffect(() => {
    function handleLocation(e) { onLocation(e.latlng) }
    map.on('locationfound', handleLocation)
    // Skip setView when a deep-link is panning the map to a specific report —
    // otherwise geolocation resolves later and steals focus from the report.
    map.locate(autoPan ? { setView: true, maxZoom: 16 } : {})
    return () => { map.off('locationfound', handleLocation) }
  }, [map, onLocation, autoPan])
  return null
}

function MapFlyTo({ target }) {
  const map = useMap()
  useEffect(() => {
    if (target) map.flyTo([target.lat, target.lon], target.zoom ?? 15)
  }, [target, map])
  return null
}

// Light: standard OpenStreetMap tiles (matches production — vivid greens
// for parks/forests). Dark: MapTiler streets-v2-dark — has Apple-Maps-style
// blue tones in water/roads. Requires a (free) MapTiler API key in
// VITE_MAPTILER_KEY (.env.local). 100k tiles/month on the free tier.
// If the key is missing we fall back to Stadia AlidadeSmoothDark so dev
// still works without setup.
const MAPTILER_KEY = import.meta.env.VITE_MAPTILER_KEY
const TILE_LIGHT = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_DARK = MAPTILER_KEY
  ? `https://api.maptiler.com/maps/streets-v2-dark/{z}/{x}/{y}.png?key=${MAPTILER_KEY}`
  : 'https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}{r}.png'
const TILE_ATTR_LIGHT =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
const TILE_ATTR_DARK = MAPTILER_KEY
  ? '&copy; <a href="https://www.maptiler.com/copyright/" target="_blank">MapTiler</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  : '&copy; <a href="https://www.stadiamaps.com/" target="_blank">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/" target="_blank">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

function Home() {
  const { t } = useTranslation()
  const { resolved: themeResolved } = useTheme()
  const isDark = themeResolved === 'dark'
  const tileUrl = isDark ? TILE_DARK : TILE_LIGHT
  const tileAttr = isDark ? TILE_ATTR_DARK : TILE_ATTR_LIGHT
  const { isAuthenticated, token } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [showOnboarding, setShowOnboarding] = useState(() => shouldShowOnboarding(searchParams))

  // Re-evaluate when ?tutorial=1 appears mid-session (e.g. from the Navbar entry).
  useEffect(() => {
    if (searchParams.get('tutorial') === '1') setShowOnboarding(true)
  }, [searchParams])

  const handleOnboardingClose = useCallback(() => {
    try { window.localStorage.setItem(ONBOARDING_FLAG, 'done') } catch {}
    setShowOnboarding(false)
    if (searchParams.get('tutorial')) {
      const next = new URLSearchParams(searchParams)
      next.delete('tutorial')
      setSearchParams(next, { replace: true })
    }
  }, [searchParams, setSearchParams])
  const [initialMapView] = useState(() => {
    try {
      const raw = sessionStorage.getItem(SESSION_MAP_VIEW_KEY)
      if (raw) return JSON.parse(raw)
    } catch {}
    return null
  })
  const queryClient = useQueryClient()
  const { data: reports = [], isLoading: loading, error } = useReports()
  // Honor /?report=ID — used by the Profile page's "Open on map" link
  // and by NotificationDropdown navigation. Lazy initial state pulls the
  // id from the URL once; the effect below re-syncs when the URL flips
  // while Home is already mounted (e.g. clicking a different notification
  // from the bell without leaving the page).
  const [selectedReportId, setSelectedReportId] = useState(() => {
    const raw = searchParams.get('report')
    const id = raw ? Number(raw) : NaN
    return Number.isFinite(id) ? id : null
  })

  useEffect(() => {
    const raw = searchParams.get('report')
    const id = Number(raw)
    if (Number.isFinite(id) && id > 0) {
      setSelectedReportId(id)
    }
  }, [searchParams])
  const [searchTarget, setSearchTarget] = useState(null)
  // Once the ?report=ID deep-link's report has loaded, fly to it. Guarded so
  // we only do this once per Home mount — later marker clicks shouldn't yank
  // the map back.
  const flewToDeepLinkRef = useRef(false)
  const [mapCenter, setMapCenter] = useState(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [newReportPin, setNewReportPin] = useState(null)
  // Reverse-geocoded place name for the new-report pin. Fetched async after
  // the pin drops; CreateReportPanel falls back to raw coordinates while it
  // resolves (or if Nominatim returns no result).
  const [newReportPinLabel, setNewReportPinLabel] = useState('')
  const [userVotes, setUserVotes] = useState({})
  const [routeMode, setRouteMode] = useState(false)
  const [routeOrigin, setRouteOrigin] = useState(null)
  const [routeDest, setRouteDest] = useState(null)
  const [routes, setRoutes] = useState(null)
  const [activeRouteIndex, setActiveRouteIndex] = useState(0)
  const [routeLoading, setRouteLoading] = useState(false)
  const [routeError, setRouteError] = useState('')
  const [toast, setToast] = useState(null)
  const [showFilters, setShowFilters] = useState(false)
  const filterButtonRef = useRef(null)
  // Filter state lives in URL so the view is shareable; this is just a
  // memoized parse of the current `?excluded=` token.
  const { types: excludedTypes, issues: excludedIssues } = parseExcluded(searchParams.get('excluded'))
  function setExcluded(nextTypes, nextIssues) {
    const params = new URLSearchParams(searchParams)
    const token = serializeExcluded(nextTypes, nextIssues)
    if (token) params.set('excluded', token)
    else params.delete('excluded')
    setSearchParams(params, { replace: true })
  }
  const handleToastDismiss = useCallback(() => setToast(null), [])
  const selectedReport = reports.find((r) => r.id === selectedReportId) ?? null

  useEffect(() => {
    // ?report cleared (panel closed via onClose) — arm for the next deep-link.
    if (!searchParams.get('report')) {
      flewToDeepLinkRef.current = false
      return
    }
    if (flewToDeepLinkRef.current) return
    if (!selectedReport) return
    setSearchTarget({ lat: selectedReport.latitude, lon: selectedReport.longitude, zoom: 18 })
    flewToDeepLinkRef.current = true
  }, [searchParams, selectedReport])

  const [routeNotice, setRouteNotice] = useState('')
  const [userLocation, setUserLocation] = useState(null)
  const [routeOriginLabel, setRouteOriginLabel] = useState('')
  const [routeDestLabel, setRouteDestLabel] = useState('')

  async function reverseGeocode(latlng) {
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${latlng.lat}&lon=${latlng.lng}&format=json`
      )
      const data = await res.json()
      const parts = data.display_name?.split(',') ?? []
      return parts.slice(0, 2).join(',').trim() || `${latlng.lat.toFixed(4)}, ${latlng.lng.toFixed(4)}`
    } catch {
      return `${latlng.lat.toFixed(4)}, ${latlng.lng.toFixed(4)}`
    }
  }

  async function fetchRoutes(origin, dest) {
    setRouteLoading(true)
    setRouteError('')
    setRouteNotice('')
    try {
      // The backend records the planned route against the caller's
      // contribution stats only when a Bearer token is supplied (see
      // RouteController.java). Without this header, /profile's "Routes
      // planned" counter never increments for signed-in users.
      const headers = { 'Content-Type': 'application/json' }
      if (token) headers.Authorization = `Bearer ${token}`
      const res = await fetch(`${import.meta.env.VITE_API_URL}/api/routes`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          startLat: origin.lat, startLon: origin.lng,
          endLat: dest.lat, endLon: dest.lng,
          mode: null,
        }),
      })
      if (!res.ok) throw new Error('Routing failed')
      const data = await res.json()
      if (!data.length) throw new Error('No routes returned')
      // Bust the cached profile so contributionStats.routesPlanned reflects
      // the new server-side count when the user navigates to /profile.
      if (token) queryClient.invalidateQueries({ queryKey: currentUserKey })
      const mapped = data.map(r => ({
        coords: decodePolyline(r.geometry),
        hasObstacles: r.hasObstacles,
        label: r.routeLabel,
        distance: r.distanceMeters,
        duration: r.durationSeconds,
      }))
      setRoutes(mapped)
      setActiveRouteIndex(0)
      const fastestHasObstacles = mapped[0]?.hasObstacles
      const hasAccessible = mapped.some(r => r.label === 'Accessible Route')
      const hasWheelchair = mapped.some(r => r.label === 'Wheelchair Route')
      const hasRamp = mapped.some(r => r.label?.includes('Ramp'))
      const missing = []
      if (fastestHasObstacles && !hasAccessible) missing.push('accessible walking')
      if (!hasWheelchair && !hasRamp) missing.push('wheelchair')
      if (missing.length) setRouteNotice(`No ${missing.join(' or ')} route could be found for this path.`)
    } catch (err) {
      const msg = err?.message || ''
      if (msg.toLowerCase().includes('route could not be found') || msg.toLowerCase().includes('unable to find')) {
        setRouteError('No walkable path found between these points. Try clicking on a road or footpath.')
      } else {
        setRouteError('Could not fetch route. Please try again.')
      }
    } finally {
      setRouteLoading(false)
    }
  }

  async function handleRouteMapClick(latlng) {
    if (!routeMode) return
    if (!routeOrigin) {
      setRouteOrigin(latlng)
      setRoutes(null)
      setRouteError('')
      setRouteNotice('')
      reverseGeocode(latlng).then(setRouteOriginLabel)
      if (routeDest) await fetchRoutes(latlng, routeDest)
      return
    }
    if (!routeDest) {
      setRouteDest(latlng)
      reverseGeocode(latlng).then(setRouteDestLabel)
      await fetchRoutes(routeOrigin, latlng)
    }
  }

  function resetRoute() {
    setRouteMode(false)
    setRouteOrigin(null)
    setRouteDest(null)
    setRouteOriginLabel('')
    setRouteDestLabel('')
    setRoutes(null)
    setActiveRouteIndex(0)
    setRouteLoading(false)
    setRouteError('')
    setRouteNotice('')
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background font-body">
      <Navbar />

      <div className="flex flex-1 overflow-hidden relative">
        {/* Route Panel — left sidebar overlay (does not resize the map) */}
        {routeMode && (
          <RoutePanel
            routeOrigin={routeOrigin}
            routeDest={routeDest}
            routeOriginLabel={routeOriginLabel}
            routeDestLabel={routeDestLabel}
            routes={routes}
            activeRouteIndex={activeRouteIndex}
            routeError={routeError}
            loading={routeLoading}
            userLocation={userLocation}
            onUseMyLocation={async () => {
              if (userLocation) {
                setRouteOrigin(userLocation)
                setRoutes(null)
                setRouteError('')
                setRouteNotice('')
                reverseGeocode(userLocation).then(setRouteOriginLabel)
                if (routeDest) await fetchRoutes(userLocation, routeDest)
              }
            }}
            onPickOrigin={async (latlng, label) => {
              setRouteOrigin(latlng)
              setRouteOriginLabel(label || '')
              setRoutes(null)
              setRouteError('')
              setRouteNotice('')
              if (routeDest) await fetchRoutes(latlng, routeDest)
            }}
            onPickDest={async (latlng, label) => {
              setRouteDest(latlng)
              setRouteDestLabel(label || '')
              setRoutes(null)
              setRouteError('')
              setRouteNotice('')
              if (routeOrigin) await fetchRoutes(routeOrigin, latlng)
            }}
            onClearOrigin={() => {
              setRouteOrigin(null)
              setRouteOriginLabel('')
              setRoutes(null)
              setRouteError('')
              setRouteNotice('')
            }}
            onClearDest={() => {
              setRouteDest(null)
              setRouteDestLabel('')
              setRoutes(null)
              setRouteError('')
              setRouteNotice('')
            }}
            onSwap={async () => {
              if (!routeOrigin || !routeDest) return
              const newOrigin = routeDest
              const newDest = routeOrigin
              setRouteOrigin(newOrigin)
              setRouteDest(newDest)
              setRouteOriginLabel(routeDestLabel)
              setRouteDestLabel(routeOriginLabel)
              setRoutes(null)
              await fetchRoutes(newOrigin, newDest)
            }}
            onSelectRoute={setActiveRouteIndex}
            onReset={resetRoute}
          />
        )}

        {/* Map area */}
        <main className="relative flex-1">
          <MapContainer
            center={initialMapView ? [initialMapView.lat, initialMapView.lng] : [41.0683, 29.0505]}
            zoom={initialMapView?.zoom ?? 16}
            zoomControl={false}
            className="w-full h-full"
            style={{ height: '100%', width: '100%' }}
          >
            <TileLayer
              key={themeResolved}
              url={tileUrl}
              attribution={tileAttr}
            />
            {reports.filter((r) => isReportVisible(r, excludedTypes, excludedIssues)).map((report) => (
              <Marker
                key={report.id}
                position={[report.latitude, report.longitude]}
                icon={makeMarkerIcon(report.status, report.primaryObjectType, report.reportType, report.id === selectedReportId)}
                zIndexOffset={report.id === selectedReportId ? 1000 : 0}
                eventHandlers={{
                  click: () => {
                    setShowCreatePanel(false)
                    setNewReportPin(null)
                    setSelectedReportId(report.id)
                  },
                }}
              />
            ))}
            {newReportPin && (
              <Marker position={newReportPin} icon={pinIcon} />
            )}
            <GeolocateOnLoad onLocation={setUserLocation} autoPan={!searchParams.get('report') && !initialMapView} />
            <MapFlyTo target={searchTarget} />
            <MapCenterTracker onCenterChange={setMapCenter} />
            <MapViewPersist />
            <MapClickHandler
              active={showCreatePanel || routeMode}
              onPick={(latlng) => {
                if (routeMode) {
                  handleRouteMapClick(latlng)
                } else {
                  setNewReportPin(latlng)
                  setNewReportPinLabel('')
                  // Resolve the human-readable place name asynchronously.
                  // CreateReportPanel renders raw coords until this resolves,
                  // so users see something immediately.
                  reverseGeocode(latlng).then(setNewReportPinLabel)
                }
              }}
            />
            {routeOrigin && <Marker position={routeOrigin} icon={pinIcon} />}
            {routeDest && <Marker position={routeDest} icon={pinIcon} />}
            {routes && routes.map((r, i) => {
              let color
              if (r.label?.includes('Accessible')) color = '#1565C0'
              else if (r.label?.includes('Wheelchair')) color = '#6A1B9A'
              else if (r.label?.includes('Ramp')) color = '#00695C'
              else color = r.hasObstacles ? '#E65100' : '#2E7D32'
              return (
                <Polyline
                  key={i}
                  positions={r.coords}
                  pathOptions={{
                    color,
                    weight: i === activeRouteIndex ? 7 : 4,
                    opacity: i === activeRouteIndex ? 1 : 0.4,
                  }}
                  eventHandlers={{ click: () => setActiveRouteIndex(i) }}
                />
              )
            })}
            <ZoomControls />
          </MapContainer>

          {/* Loading / error overlay */}
          {(loading || error) && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-[500]">
              {loading && (
                <p className="text-on-surface-variant font-bold text-lg select-none bg-surface-container-lowest/80 px-6 py-3 rounded-2xl shadow">
                  Loading reports...
                </p>
              )}
              {error && (
                <p className="text-error font-bold text-lg select-none bg-surface-container-lowest/80 px-6 py-3 rounded-2xl shadow">
                  Failed to load reports.
                </p>
              )}
            </div>
          )}

          {/* Pin drop hint */}
          {showCreatePanel && !newReportPin && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none max-w-[calc(100%-2rem)]">
              <div className="bg-primary text-on-primary px-4 sm:px-6 py-2.5 sm:py-3 rounded-full shadow-lg font-semibold text-xs sm:text-sm flex items-center gap-2 whitespace-nowrap">
                <span className="material-symbols-outlined text-base">location_on</span>
                {t('home.pinDropHint')}
              </div>
            </div>
          )}

          {/* Route mode click hint */}
          {routeMode && !routeOrigin && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none max-w-[calc(100%-2rem)]">
              <div className="bg-primary text-on-primary px-4 sm:px-6 py-2.5 sm:py-3 rounded-full shadow-lg font-semibold text-xs sm:text-sm flex items-center gap-2 whitespace-nowrap">
                <span className="material-symbols-outlined text-base">route</span>
                {t('home.routeStartHint')}
              </div>
            </div>
          )}
          {routeMode && routeOrigin && !routeDest && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none max-w-[calc(100%-2rem)]">
              <div className="bg-primary text-on-primary px-4 sm:px-6 py-2.5 sm:py-3 rounded-full shadow-lg font-semibold text-xs sm:text-sm flex items-center gap-2 whitespace-nowrap">
                <span className="material-symbols-outlined text-base">route</span>
                {t('home.routeDestHint')}
              </div>
            </div>
          )}

          {/* Route notice toast */}
          {routeNotice && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] flex items-center gap-3 bg-amber-50 border border-amber-200 text-amber-800 px-4 sm:px-5 py-2.5 sm:py-3 rounded-2xl shadow-lg max-w-[calc(100%-2rem)] sm:max-w-sm text-xs sm:text-sm font-semibold">
              <span className="material-symbols-outlined text-amber-600 text-base flex-shrink-0">warning</span>
              <span>{routeNotice}</span>
              <button onClick={() => setRouteNotice('')} className="ml-1 text-amber-600 hover:text-amber-800" aria-label="Dismiss">
                <span className="material-symbols-outlined text-base">close</span>
              </button>
            </div>
          )}

          <MapSearchBar
            mapCenter={mapCenter}
            onLocationPicked={({ lat, lon }) => setSearchTarget({ lat, lon })}
            filterSlot={
              <>
                <button
                  ref={filterButtonRef}
                  type="button"
                  onClick={() => setShowFilters((v) => !v)}
                  className="p-2 hover:bg-primary/10 rounded-lg transition-colors cursor-pointer"
                  aria-label={t('home.filter')}
                  aria-expanded={showFilters}
                >
                  <span className="material-symbols-outlined text-secondary">tune</span>
                  {excludedCount(excludedTypes, excludedIssues) > 0 && (
                    <span
                      className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-primary text-on-primary text-[10px] font-bold flex items-center justify-center"
                      aria-label={t('home.filtersActive', { count: excludedCount(excludedTypes, excludedIssues) })}
                    >
                      {excludedCount(excludedTypes, excludedIssues)}
                    </span>
                  )}
                </button>
                {showFilters && (
                  <MapFilters
                    excludedTypes={excludedTypes}
                    excludedIssues={excludedIssues}
                    onChange={setExcluded}
                    onClose={() => setShowFilters(false)}
                    triggerRef={filterButtonRef}
                  />
                )}
              </>
            }
          />

          {/* Community Pulse card + FAB */}
          <div className="absolute bottom-4 right-4 sm:bottom-10 sm:right-10 z-[1000] flex flex-col items-end gap-3 sm:gap-4">
            <div className="hidden lg:block bg-surface-container-lowest/80 backdrop-blur-md rounded-3xl p-6 w-72 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-outline-variant/20">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-headline font-bold text-on-surface">{t('home.communityPulse')}</h3>
                <span className="material-symbols-outlined text-primary">analytics</span>
              </div>
              <div className="space-y-4">
                <div className="flex gap-3">
                  <div className="w-12 h-12 rounded-xl bg-primary-container/40 flex-shrink-0 flex items-center justify-center">
                    <span className="material-symbols-outlined text-primary">trending_up</span>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-on-surface">{t('home.activeReports', { count: reports.length })}</p>
                    <p className="text-[10px] text-on-surface-variant">{t('home.withinRange')}</p>
                  </div>
                </div>
                <div className="bg-primary/5 rounded-2xl p-4">
                  <div className="flex justify-between text-[10px] font-bold mb-2">
                    <span className="text-on-surface">{t('home.cityResolutionRate')}</span>
                    <span className="text-primary">84%</span>
                  </div>
                  <div className="h-1.5 w-full bg-surface-container rounded-full overflow-hidden">
                    <div className="h-full bg-primary rounded-full" style={{ width: '84%' }} />
                  </div>
                </div>
              </div>
            </div>

            <button
              onClick={() => { setRouteMode(true); setRouteOrigin(null); setRouteDest(null); setRoutes(null); setRouteError('') }}
              aria-label={t('home.getRoutes')}
              className={`h-12 sm:h-14 px-4 sm:px-7 rounded-full shadow-lg flex items-center gap-2 sm:gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide ${
                routeMode ? 'bg-secondary text-on-secondary' : 'bg-surface-container-lowest/90 text-on-surface border border-outline-variant/20'
              }`}
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>route</span>
              <span className="hidden sm:inline">{t('home.getRoutes')}</span>
            </button>
            <button
              onClick={() => isAuthenticated ? setShowCreatePanel(true) : navigate('/login')}
              aria-label={t('home.reportIssue')}
              className="bg-primary text-on-primary h-12 sm:h-14 px-4 sm:px-7 rounded-full shadow-lg flex items-center gap-2 sm:gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide"
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>add_circle</span>
              <span className="hidden sm:inline">{t('home.reportIssue')}</span>
            </button>
          </div>

        </main>

        {/* Report Panel sidebar */}
        {selectedReport && (
          <ReportPanel
            key={selectedReport.id}
            report={selectedReport}
            userVote={userVotes[selectedReport.id] ?? selectedReport.userVote ?? null}
            onVoteChange={(vote) => setUserVotes(prev => ({ ...prev, [selectedReport.id]: vote }))}
            // Toast lives on Home so it survives the panel unmounting
            // (e.g. after a successful delete that closes the panel).
            onShowToast={(t) => setToast(t)}
            onClose={() => { setSelectedReportId(null); navigate('/', { replace: true }) }}
            onVoteUpdate={(updatedReport) => {
              setSelectedReportId(updatedReport.id)
              queryClient.setQueryData(reportKeys.lists(), (prev) =>
                prev?.map((r) => r.id === updatedReport.id ? updatedReport : r)
              )
            }}
          />
        )}
      </div>

      {/* Create Report Panel */}
      {showCreatePanel && (
        <CreateReportPanel
          position={newReportPin}
          positionLabel={newReportPinLabel}
          onClose={() => { setShowCreatePanel(false); setNewReportPin(null); setNewReportPinLabel('') }}
          onCreated={() => {
            queryClient.invalidateQueries({ queryKey: reportKeys.lists() })
            setNewReportPin(null)
            setNewReportPinLabel('')
            setToast({ message: 'Report submitted successfully!', type: 'success' })
          }}
          onError={(message) => setToast({ message, type: 'error' })}
        />
      )}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onDismiss={handleToastDismiss}
        />
      )}
      {showOnboarding && <OnboardingTutorial onClose={handleOnboardingClose} />}
    </div>
  )
}

export default Home
