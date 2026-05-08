import { useState, useEffect, useRef, useCallback } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { useQueryClient } from '@tanstack/react-query'
import Navbar from '../components/Navbar.jsx'
import ReportPanel from '../components/ReportPanel.jsx'
import CreateReportPanel from '../components/CreateReportPanel.jsx'
import RoutePanel from '../components/RoutePanel.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'
import Toast from '../components/Toast.jsx'
import { useReports, reportKeys } from '../hooks/useReports.js'

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

function makeMarkerIcon(status, tag, selected = false) {
  const cfg = REPORT_TAGS[tag] ?? { icon: 'warning', color: '#767777' }
  const borderColor = status === 'verified' ? '#176a21' : cfg.color
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
        transition:width 150ms ease, height 150ms ease;
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
  return (
    <div className="absolute right-3 sm:right-10 top-24 sm:top-1/3 sm:-translate-y-1/2 flex flex-col gap-2 z-[1000]">
      <button
        onClick={() => map.zoomIn()}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors"
        aria-label="Zoom in"
      >
        <span className="material-symbols-outlined">add</span>
      </button>
      <button
        onClick={() => map.zoomOut()}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors"
        aria-label="Zoom out"
      >
        <span className="material-symbols-outlined">remove</span>
      </button>
      <button
        onClick={() => map.locate({ setView: true, maxZoom: 16 })}
        className="w-10 h-10 sm:w-12 sm:h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors mt-2"
        aria-label="My location"
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

function Home() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const { data: reports = [], isLoading: loading, error } = useReports()
  // Honor /?report=ID — used by the Profile page's "Open on map" link.
  // Lazy initial state pulls the id from the URL once; the panel opens as soon as the
  // matching report is in the loaded list.
  const [selectedReportId, setSelectedReportId] = useState(() => {
    const raw = searchParams.get('report')
    const id = raw ? Number(raw) : NaN
    return Number.isFinite(id) ? id : null
  })
  const [searchValue, setSearchValue] = useState('Boğaziçi, Istanbul')
  const [searchTarget, setSearchTarget] = useState(null)
  // Once the ?report=ID deep-link's report has loaded, fly to it. Guarded so
  // we only do this once per Home mount — later marker clicks shouldn't yank
  // the map back.
  const flewToDeepLinkRef = useRef(false)
  const [mapCenter, setMapCenter] = useState(null)
  const [searchError, setSearchError] = useState('')
  const [searchSuggestions, setSearchSuggestions] = useState([])
  const searchDebounce = useRef(null)
  const [showCreatePanel, setShowCreatePanel] = useState(false)
  const [newReportPin, setNewReportPin] = useState(null)
  const [userVotes, setUserVotes] = useState({})
  const [routeMode, setRouteMode] = useState(false)
  const [routeOrigin, setRouteOrigin] = useState(null)
  const [routeDest, setRouteDest] = useState(null)
  const [routes, setRoutes] = useState(null)
  const [activeRouteIndex, setActiveRouteIndex] = useState(0)
  const [routeLoading, setRouteLoading] = useState(false)
  const [routeError, setRouteError] = useState('')
  const [toast, setToast] = useState(null)
  const handleToastDismiss = useCallback(() => setToast(null), [])
  const selectedReport = reports.find((r) => r.id === selectedReportId) ?? null

  useEffect(() => {
    if (flewToDeepLinkRef.current) return
    if (!searchParams.get('report')) return
    if (!selectedReport) return
    setSearchTarget({ lat: selectedReport.latitude, lon: selectedReport.longitude, zoom: 18 })
    flewToDeepLinkRef.current = true
  }, [searchParams, selectedReport])

  function handleSearchChange(e) {
    const query = e.target.value
    setSearchValue(query)
    setSearchError('')
    clearTimeout(searchDebounce.current)
    if (query.trim().length < 2) { setSearchSuggestions([]); return }
    searchDebounce.current = setTimeout(async () => {
      try {
        const viewboxParam = mapCenter
          ? `&viewbox=${mapCenter.lng - 2},${mapCenter.lat - 2},${mapCenter.lng + 2},${mapCenter.lat + 2}`
          : ''
        const res = await fetch(
          `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=10&addressdetails=1${viewboxParam}`
        )
        let results = await res.json()
        if (mapCenter) {
          results = results.sort((a, b) => {
            const distA = Math.hypot(parseFloat(a.lat) - mapCenter.lat, parseFloat(a.lon) - mapCenter.lng)
            const distB = Math.hypot(parseFloat(b.lat) - mapCenter.lat, parseFloat(b.lon) - mapCenter.lng)
            return distA - distB
          })
        }
        setSearchSuggestions(results.slice(0, 5))
      } catch {
        setSearchSuggestions([])
      }
    }, 400)
  }

  function handleSuggestionSelect(suggestion) {
    setSearchValue(suggestion.display_name)
    setSearchSuggestions([])
    setSearchTarget({ lat: parseFloat(suggestion.lat), lon: parseFloat(suggestion.lon) })
  }

  async function handleSearchSubmit(e) {
    if (e.key === 'Escape') { setSearchSuggestions([]); e.target.blur(); return }
    if (e.key !== 'Enter') return
    const query = searchValue.trim()
    if (!query) return
    setSearchError('')
    setSearchSuggestions([])
    e.target.blur()
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`
      )
      const results = await res.json()
      if (!results.length) { setSearchError('No results found.'); return }
      setSearchTarget({ lat: parseFloat(results[0].lat), lon: parseFloat(results[0].lon) })
    } catch {
      setSearchError('Search failed. Please try again.')
    }
  }
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
      const res = await fetch(`${import.meta.env.VITE_API_URL}/api/routes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          startLat: origin.lat, startLon: origin.lng,
          endLat: dest.lat, endLon: dest.lng,
          mode: null,
        }),
      })
      if (!res.ok) throw new Error('Routing failed')
      const data = await res.json()
      if (!data.length) throw new Error('No routes returned')
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
          <div className="absolute top-0 left-0 w-full sm:w-auto h-full z-[1000] pointer-events-auto">
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
          </div>
        )}

        {/* Map area */}
        <main className="relative flex-1">
          <MapContainer
            center={[41.0683, 29.0505]}
            zoom={16}
            zoomControl={false}
            className="w-full h-full"
            style={{ height: '100%', width: '100%' }}
          >
            <TileLayer
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            />
            {reports.map((report) => (
              <Marker
                key={report.id}
                position={[report.latitude, report.longitude]}
                icon={makeMarkerIcon(report.status, report.tags[0], report.id === selectedReportId)}
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
            <GeolocateOnLoad onLocation={setUserLocation} autoPan={!searchParams.get('report')} />
            <MapFlyTo target={searchTarget} />
            <MapCenterTracker onCenterChange={setMapCenter} />
            <MapClickHandler
              active={showCreatePanel || routeMode}
              onPick={(latlng) => {
                if (routeMode) handleRouteMapClick(latlng)
                else setNewReportPin(latlng)
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
                <p className="text-on-surface-variant font-bold text-lg select-none bg-white/80 px-6 py-3 rounded-2xl shadow">
                  Loading reports...
                </p>
              )}
              {error && (
                <p className="text-error font-bold text-lg select-none bg-white/80 px-6 py-3 rounded-2xl shadow">
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
                Click on the map to set report location
              </div>
            </div>
          )}

          {/* Route mode click hint */}
          {routeMode && !routeOrigin && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none max-w-[calc(100%-2rem)]">
              <div className="bg-primary text-on-primary px-4 sm:px-6 py-2.5 sm:py-3 rounded-full shadow-lg font-semibold text-xs sm:text-sm flex items-center gap-2 whitespace-nowrap">
                <span className="material-symbols-outlined text-base">route</span>
                Click on the map to set your starting point
              </div>
            </div>
          )}
          {routeMode && routeOrigin && !routeDest && (
            <div className="absolute bottom-24 sm:bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none max-w-[calc(100%-2rem)]">
              <div className="bg-primary text-on-primary px-4 sm:px-6 py-2.5 sm:py-3 rounded-full shadow-lg font-semibold text-xs sm:text-sm flex items-center gap-2 whitespace-nowrap">
                <span className="material-symbols-outlined text-base">route</span>
                Now click your destination
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

          {/* Floating search bar */}
          <div className="absolute top-3 sm:top-6 left-2 right-2 sm:left-1/2 sm:right-auto sm:-translate-x-1/2 sm:w-full sm:max-w-2xl sm:px-6 z-[1000] pointer-events-none">
            <div className="flex items-center bg-white/80 backdrop-blur-md rounded-2xl px-3 sm:px-6 py-2 sm:py-3 gap-2 sm:gap-4 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-white/20 pointer-events-auto">
              <span className="material-symbols-outlined text-primary text-xl sm:text-2xl">location_on</span>
              <div className="flex-1 min-w-0">
                <p className="hidden sm:block text-[10px] uppercase tracking-wider font-bold text-secondary">Current Location</p>
                <input
                  type="text"
                  value={searchValue}
                  onChange={handleSearchChange}
                  onKeyDown={handleSearchSubmit}
                  onFocus={() => setSearchValue('')}
                  onBlur={() => setTimeout(() => setSearchSuggestions([]), 150)}
                  placeholder="Search location..."
                  className="bg-transparent border-none p-0 w-full text-on-surface font-headline font-semibold focus:ring-0 text-sm outline-none"
                />
              </div>
              <div className="hidden sm:block h-8 w-px bg-outline-variant/30" />
              <button className="p-2 hover:bg-primary/10 rounded-lg transition-colors flex-shrink-0" aria-label="Filter">
                <span className="material-symbols-outlined text-secondary">tune</span>
              </button>
            </div>
            {searchSuggestions.length > 0 && (
              <ul className="mt-1 bg-white rounded-2xl shadow-lg border border-outline-variant/10 overflow-hidden pointer-events-auto">
                {searchSuggestions.map((s) => (
                  <li key={s.place_id}>
                    <button
                      onMouseDown={() => handleSuggestionSelect(s)}
                      className="w-full text-left px-5 py-3 text-sm text-on-surface hover:bg-primary/5 flex items-center gap-3"
                    >
                      <span className="material-symbols-outlined text-base text-primary flex-shrink-0">location_on</span>
                      <span className="truncate">{s.display_name}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {searchError && (
              <p className="mt-2 text-xs text-error bg-white/90 rounded-xl px-4 py-2 shadow">{searchError}</p>
            )}
          </div>

          {/* Community Pulse card + FAB */}
          <div className="absolute bottom-4 right-4 sm:bottom-10 sm:right-10 z-[1000] flex flex-col items-end gap-3 sm:gap-4">
            <div className="hidden lg:block bg-white/80 backdrop-blur-md rounded-3xl p-6 w-72 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-white/20">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-headline font-bold text-on-surface">Community Pulse</h3>
                <span className="material-symbols-outlined text-primary">analytics</span>
              </div>
              <div className="space-y-4">
                <div className="flex gap-3">
                  <div className="w-12 h-12 rounded-xl bg-primary-container/40 flex-shrink-0 flex items-center justify-center">
                    <span className="material-symbols-outlined text-primary">trending_up</span>
                  </div>
                  <div>
                    <p className="text-xs font-bold">{reports.length} Active Reports</p>
                    <p className="text-[10px] text-secondary">Within 500m of your location</p>
                  </div>
                </div>
                <div className="bg-primary/5 rounded-2xl p-4">
                  <div className="flex justify-between text-[10px] font-bold mb-2">
                    <span>City Resolution Rate</span>
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
              aria-label="Get Routes"
              className={`h-12 sm:h-14 px-4 sm:px-7 rounded-full shadow-lg flex items-center gap-2 sm:gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide ${
                routeMode ? 'bg-secondary text-on-secondary' : 'bg-white/90 text-on-surface border border-outline-variant/20'
              }`}
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>route</span>
              <span className="hidden sm:inline">Get Routes</span>
            </button>
            <button
              onClick={() => isAuthenticated ? setShowCreatePanel(true) : navigate('/login')}
              aria-label="Report an Issue"
              className="bg-primary text-white h-12 sm:h-14 px-4 sm:px-7 rounded-full shadow-lg flex items-center gap-2 sm:gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide"
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>add_circle</span>
              <span className="hidden sm:inline">Report an Issue</span>
            </button>
          </div>

        </main>

        {/* Report Panel sidebar */}
        {selectedReport && (
          <ReportPanel
            key={selectedReport.id}
            report={selectedReport}
            userVote={userVotes[selectedReport.id] ?? null}
            onVoteChange={(vote) => setUserVotes(prev => ({ ...prev, [selectedReport.id]: vote }))}
            onClose={() => setSelectedReportId(null)}
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
          onClose={() => { setShowCreatePanel(false); setNewReportPin(null) }}
          onCreated={() => {
            queryClient.invalidateQueries({ queryKey: reportKeys.lists() })
            setNewReportPin(null)
            setToast({ message: 'Report submitted successfully!', type: 'success' })
          }}
        />
      )}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onDismiss={handleToastDismiss}
        />
      )}
    </div>
  )
}

export default Home
