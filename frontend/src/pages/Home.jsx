import { useState, useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import Navbar from '../components/Navbar.jsx'
import ReportPanel from '../components/ReportPanel.jsx'
import CreateReportPanel from '../components/CreateReportPanel.jsx'
import RoutePanel from '../components/RoutePanel.jsx'
import { getReports, mapReport } from '../services/reportService.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useNavigate } from 'react-router-dom'

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

function makeMarkerIcon(status) {
  const borderColor = status === 'verified' ? '#176a21' : '#f59e0b'
  const iconColor = status === 'verified' ? '#176a21' : '#d97706'
  return L.divIcon({
    className: '',
    html: `
      <div style="
        width:40px;height:40px;
        background:white;
        border-radius:50%;
        border:2.5px solid ${borderColor};
        box-shadow:0 4px 12px rgba(0,0,0,0.15);
        display:flex;align-items:center;justify-content:center;
        cursor:pointer;
      ">
        <span class="material-symbols-outlined" style="
          font-size:20px;
          color:${iconColor};
          font-variation-settings:'FILL' 1;
          line-height:1;
        ">warning</span>
      </div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -24],
  })
}

function ZoomControls() {
  const map = useMap()
  return (
    <div className="absolute right-10 top-1/3 -translate-y-1/2 flex flex-col gap-2 z-[1000]">
      <button
        onClick={() => map.zoomIn()}
        className="w-12 h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors"
        aria-label="Zoom in"
      >
        <span className="material-symbols-outlined">add</span>
      </button>
      <button
        onClick={() => map.zoomOut()}
        className="w-12 h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors"
        aria-label="Zoom out"
      >
        <span className="material-symbols-outlined">remove</span>
      </button>
      <button
        onClick={() => map.locate({ setView: true, maxZoom: 16 })}
        className="w-12 h-12 bg-white/80 backdrop-blur-md rounded-xl flex items-center justify-center shadow-lg hover:bg-white text-secondary hover:text-primary transition-colors mt-2"
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


function GeolocateOnLoad({ onLocation }) {
  const map = useMap()
  useEffect(() => {
    function handleLocation(e) { onLocation(e.latlng) }
    map.on('locationfound', handleLocation)
    map.locate({ setView: true, maxZoom: 16 })
    return () => { map.off('locationfound', handleLocation) }
  }, [map, onLocation])
  return null
}

function Home() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [reports, setReports] = useState([])
  const [selectedReport, setSelectedReport] = useState(null)
  const [searchValue, setSearchValue] = useState('Boğaziçi, Istanbul')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
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
  const [routeNotice, setRouteNotice] = useState('')
  const [userLocation, setUserLocation] = useState(null)

  async function handleRouteMapClick(latlng) {
    if (!routeMode) return
    if (!routeOrigin) {
      setRouteOrigin(latlng)
      setRouteDest(null)
      setRoutes(null)
      setRouteError('')
      return
    }
    if (!routeDest) {
      setRouteDest(latlng)
      setRouteLoading(true)
      setRouteError('')
      setRouteNotice('')
      try {
        const res = await fetch(`${import.meta.env.VITE_API_URL}/api/routes`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            startLat: routeOrigin.lat,
            startLon: routeOrigin.lng,
            endLat: latlng.lat,
            endLon: latlng.lng,
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
  }

  function resetRoute() {
    setRouteMode(false)
    setRouteOrigin(null)
    setRouteDest(null)
    setRoutes(null)
    setActiveRouteIndex(0)
    setRouteLoading(false)
    setRouteError('')
    setRouteNotice('')
  }

  useEffect(() => {
    async function fetchReports() {
      try {
        const data = await getReports()
        setReports(data.map(mapReport))
      } catch (err) {
        setError('Failed to load reports.')
        console.error(err)
      } finally {
        setLoading(false)
      }
    }
    fetchReports()
  }, [])

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background font-body">
      <Navbar />

      <div className="flex flex-1 overflow-hidden">
        {/* Route Panel — left sidebar */}
        {routeMode && (
          <RoutePanel
            routeOrigin={routeOrigin}
            routeDest={routeDest}
            routes={routes}
            activeRouteIndex={activeRouteIndex}
            routeError={routeError}
            loading={routeLoading}
            userLocation={userLocation}
            onUseMyLocation={() => {
              if (userLocation) {
                setRouteOrigin(userLocation)
                setRouteDest(null)
                setRoutes(null)
                setRouteError('')
                setRouteNotice('')
              }
            }}
            onSelectRoute={setActiveRouteIndex}
            onReset={resetRoute}
          />
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
                icon={makeMarkerIcon(report.status)}
                eventHandlers={{ click: () => setSelectedReport(report) }}
              />
            ))}
            {newReportPin && (
              <Marker position={newReportPin} icon={pinIcon} />
            )}
            <GeolocateOnLoad onLocation={setUserLocation} />
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
                  {error}
                </p>
              )}
            </div>
          )}

          {/* Pin drop hint */}
          {showCreatePanel && !newReportPin && (
            <div className="absolute top-6 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none">
              <div className="bg-primary text-on-primary px-6 py-3 rounded-full shadow-lg font-semibold text-sm flex items-center gap-2">
                <span className="material-symbols-outlined text-base">location_on</span>
                Click on the map to set report location
              </div>
            </div>
          )}

          {/* Route mode click hint */}
          {routeMode && !routeOrigin && (
            <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none">
              <div className="bg-primary text-on-primary px-6 py-3 rounded-full shadow-lg font-semibold text-sm flex items-center gap-2">
                <span className="material-symbols-outlined text-base">route</span>
                Click on the map to set your starting point
              </div>
            </div>
          )}
          {routeMode && routeOrigin && !routeDest && (
            <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-[1000] pointer-events-none">
              <div className="bg-primary text-on-primary px-6 py-3 rounded-full shadow-lg font-semibold text-sm flex items-center gap-2">
                <span className="material-symbols-outlined text-base">route</span>
                Now click your destination
              </div>
            </div>
          )}

          {/* Route notice toast */}
          {routeNotice && (
            <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-[1000] flex items-center gap-3 bg-amber-50 border border-amber-200 text-amber-800 px-5 py-3 rounded-2xl shadow-lg max-w-sm text-sm font-semibold">
              <span className="material-symbols-outlined text-amber-600 text-base flex-shrink-0">warning</span>
              <span>{routeNotice}</span>
              <button onClick={() => setRouteNotice('')} className="ml-1 text-amber-600 hover:text-amber-800" aria-label="Dismiss">
                <span className="material-symbols-outlined text-base">close</span>
              </button>
            </div>
          )}

          {/* Floating search bar */}
          <div className="absolute top-6 left-1/2 -translate-x-1/2 w-full max-w-2xl px-6 z-[1000] pointer-events-none">
            <div className="flex items-center bg-white/80 backdrop-blur-md rounded-2xl px-6 py-3 gap-4 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-white/20 pointer-events-auto">
              <span className="material-symbols-outlined text-primary">location_on</span>
              <div className="flex-1">
                <p className="text-[10px] uppercase tracking-wider font-bold text-secondary">Current Location</p>
                <input
                  type="text"
                  value={searchValue}
                  onChange={(e) => setSearchValue(e.target.value)}
                  className="bg-transparent border-none p-0 w-full text-on-surface font-headline font-semibold focus:ring-0 text-sm outline-none"
                />
              </div>
              <div className="h-8 w-px bg-outline-variant/30" />
              <button className="p-2 hover:bg-primary/10 rounded-lg transition-colors" aria-label="Filter">
                <span className="material-symbols-outlined text-secondary">tune</span>
              </button>
            </div>
          </div>

          {/* Community Pulse card + FAB */}
          <div className="absolute bottom-10 right-10 z-[1000] flex flex-col items-end gap-4">
            <div className="bg-white/80 backdrop-blur-md rounded-3xl p-6 w-72 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-white/20">
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
              className={`h-14 px-7 rounded-full shadow-lg flex items-center gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide ${
                routeMode ? 'bg-secondary text-on-secondary' : 'bg-white/90 text-on-surface border border-outline-variant/20'
              }`}
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>route</span>
              Get Routes
            </button>
            <button
              onClick={() => isAuthenticated ? setShowCreatePanel(true) : navigate('/login')}
              className="bg-primary text-white h-14 px-7 rounded-full shadow-lg flex items-center gap-3 hover:scale-105 active:scale-95 transition-all font-headline font-bold tracking-wide"
            >
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>add_circle</span>
              Report an Issue
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
            onClose={() => setSelectedReport(null)}
            onVoteUpdate={(updatedReport) => {
              setReports(prev => prev.map(r => r.id === updatedReport.id ? updatedReport : r))
              setSelectedReport(updatedReport)
            }}
          />
        )}
      </div>

      {/* Create Report Panel */}
      {showCreatePanel && (
        <CreateReportPanel
          position={newReportPin}
          onClose={() => { setShowCreatePanel(false); setNewReportPin(null) }}
          onCreated={(newReport) => { setReports(prev => [...prev, newReport]); setNewReportPin(null) }}
        />
      )}
    </div>
  )
}

export default Home
