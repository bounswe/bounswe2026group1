import { useState, useRef, useEffect } from 'react'

/**
 * RoutePanel
 * Left sidebar shown when route mode is active.
 * Props:
 *  - routeOrigin: {lat, lng} | null
 *  - routeDest: {lat, lng} | null
 *  - routes: Array<{coords, label, distance, duration, hasObstacles}> | null
 *  - activeRouteIndex: number
 *  - routeError: string
 *  - loading: bool
 *  - userLocation: {lat, lng} | null
 *  - onUseMyLocation: () => void
 *  - onSwap: () => void
 *  - onClearOrigin: () => void
 *  - onClearDest: () => void
 *  - onPickOrigin: ({lat, lng}) => void
 *  - onPickDest: ({lat, lng}) => void
 *  - onSelectRoute: (index) => void
 *  - onReset: () => void
 */
function RoutePanel({
  routeOrigin,
  routeDest,
  routeOriginLabel,
  routeDestLabel,
  routes,
  activeRouteIndex,
  routeError,
  loading,
  userLocation,
  onUseMyLocation,
  onSwap,
  onClearOrigin,
  onClearDest,
  onPickOrigin,
  onPickDest,
  onSelectRoute,
  onReset,
}) {
  const [originQuery, setOriginQuery] = useState('')
  const [destQuery, setDestQuery] = useState('')
  const [originSuggestions, setOriginSuggestions] = useState([])
  const [destSuggestions, setDestSuggestions] = useState([])
  const originDebounce = useRef(null)
  const destDebounce = useRef(null)

  // Bottom-sheet drag-to-resize on mobile (desktop keeps left-sidebar layout).
  const SHEET_SNAP_POINTS = [25, 60, 90]
  const SHEET_DISMISS_THRESHOLD = 15
  const SHEET_DEFAULT_DVH = 60
  const [sheetHeightDvh, setSheetHeightDvh] = useState(SHEET_DEFAULT_DVH)
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef({ startY: 0, startHeight: SHEET_DEFAULT_DVH, active: false })

  const [isMobileSheet, setIsMobileSheet] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(max-width: 1023px)').matches
      : true
  )
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const mql = window.matchMedia('(max-width: 1023px)')
    function onChange() { setIsMobileSheet(mql.matches) }
    mql.addEventListener?.('change', onChange)
    return () => mql.removeEventListener?.('change', onChange)
  }, [])

  function handleHandlePointerDown(e) {
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = { startY: e.clientY, startHeight: sheetHeightDvh, active: true }
    setIsDragging(true)
  }
  function handleHandlePointerMove(e) {
    if (!dragRef.current.active) return
    const deltaY = e.clientY - dragRef.current.startY
    const dvhDelta = (deltaY / window.innerHeight) * 100
    const next = dragRef.current.startHeight - dvhDelta
    setSheetHeightDvh(Math.max(5, Math.min(95, next)))
  }
  function handleHandlePointerUp(e) {
    if (!dragRef.current.active) return
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch { /* noop */ }
    dragRef.current.active = false
    setIsDragging(false)
    setSheetHeightDvh(prev => {
      if (prev < SHEET_DISMISS_THRESHOLD) {
        onReset()
        return SHEET_DEFAULT_DVH
      }
      return SHEET_SNAP_POINTS.reduce(
        (best, p) => (Math.abs(p - prev) < Math.abs(best - prev) ? p : best),
        SHEET_SNAP_POINTS[0],
      )
    })
  }

  function routeColor(route) {
    if (route.label?.includes('Accessible')) return '#1565C0'
    if (route.label?.includes('Wheelchair')) return '#6A1B9A'
    if (route.label?.includes('Ramp'))       return '#00695C'
    return route.hasObstacles ? '#E65100' : '#2E7D32'
  }

  function formatDistance(m) {
    return m >= 1000 ? `${(m / 1000).toFixed(2)} km` : `${Math.round(m)} m`
  }

  function formatDuration(s) {
    const mins = Math.ceil(s / 60)
    return mins >= 60 ? `${Math.floor(mins / 60)}h ${mins % 60}m` : `${mins} min`
  }

  async function fetchSuggestions(query, setSuggestions) {
    if (!query.trim()) { setSuggestions([]); return }
    const viewbox = userLocation
      ? `${userLocation.lng - 0.05},${userLocation.lat - 0.05},${userLocation.lng + 0.05},${userLocation.lat + 0.05}`
      : null
    const params = new URLSearchParams({
      q: query, format: 'json', limit: '5', addressdetails: '1',
      ...(viewbox ? { viewbox, bounded: '0' } : {}),
    })
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?${params}`)
      const data = await res.json()
      setSuggestions(data)
    } catch {
      setSuggestions([])
    }
  }

  function handleOriginChange(e) {
    const val = e.target.value
    setOriginQuery(val)
    clearTimeout(originDebounce.current)
    originDebounce.current = setTimeout(() => fetchSuggestions(val, setOriginSuggestions), 400)
  }

  function handleDestChange(e) {
    const val = e.target.value
    setDestQuery(val)
    clearTimeout(destDebounce.current)
    destDebounce.current = setTimeout(() => fetchSuggestions(val, setDestSuggestions), 400)
  }

  function selectOrigin(place) {
    const label = place.display_name.split(',').slice(0, 2).join(',').trim()
    setOriginQuery(label)
    setOriginSuggestions([])
    onPickOrigin({ lat: parseFloat(place.lat), lng: parseFloat(place.lon) }, label)
  }

  function selectDest(place) {
    const label = place.display_name.split(',').slice(0, 2).join(',').trim()
    setDestQuery(label)
    setDestSuggestions([])
    onPickDest({ lat: parseFloat(place.lat), lng: parseFloat(place.lon) }, label)
  }

  // Clear search inputs when points are cleared externally
  useEffect(() => { if (!routeOrigin) setOriginQuery('') }, [routeOrigin])
  useEffect(() => { if (!routeDest) setDestQuery('') }, [routeDest])

  const step = !routeOrigin ? 1 : !routeDest ? 2 : 3

  return (
    <div
      className="fixed inset-0 z-[1200] pointer-events-none flex"
      style={{
        flexDirection: isMobileSheet ? 'column' : 'row',
        justifyContent: isMobileSheet ? 'flex-end' : 'flex-start',
      }}
    >
      <aside
        style={isMobileSheet
          ? {
              height: `${sheetHeightDvh}dvh`,
              maxHeight: `${sheetHeightDvh}dvh`,
              width: '100%',
              borderTopLeftRadius: '32px',
              borderTopRightRadius: '32px',
              borderTop: '1px solid rgba(172,173,173,.2)',
              boxShadow: '0 -10px 40px rgba(0,0,0,0.2)',
            }
          : {
              width: '360px',
              height: '100%',
              maxHeight: '100%',
              borderRight: '1px solid rgba(172,173,173,.1)',
            }}
        className={`pointer-events-auto bg-surface-container-low flex flex-col relative overflow-y-auto ${isDragging ? '' : 'transition-[height,max-height] duration-200 ease-out'}`}
      >
        {/* Mobile drag handle — pill is decorative, the wider hit-area carries the pointer events. */}
        {isMobileSheet && (
          <div
            role="slider"
            aria-label="Resize route panel"
            aria-valuemin={SHEET_SNAP_POINTS[0]}
            aria-valuemax={SHEET_SNAP_POINTS[SHEET_SNAP_POINTS.length - 1]}
            aria-valuenow={Math.round(sheetHeightDvh)}
            tabIndex={-1}
            onPointerDown={handleHandlePointerDown}
            onPointerMove={handleHandlePointerMove}
            onPointerUp={handleHandlePointerUp}
            onPointerCancel={handleHandlePointerUp}
            className="flex-shrink-0 pt-3 pb-2 cursor-grab active:cursor-grabbing touch-none select-none"
          >
            <div className="w-12 h-1.5 bg-outline-variant/40 rounded-full mx-auto" />
          </div>
        )}

      {/* Header */}
      <div className="px-6 pt-2 lg:pt-6 pb-4 flex items-center justify-between flex-shrink-0">
        <div>
          <h2 className="text-xl font-extrabold font-headline text-on-surface">Get Routes</h2>
          <p className="text-xs text-on-surface-variant mt-0.5">Accessibility-aware navigation</p>
        </div>
        <button
          onClick={onReset}
          className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center hover:bg-error/10 hover:text-error transition-colors"
          aria-label="Close route panel"
        >
          <span className="material-symbols-outlined text-base">close</span>
        </button>
      </div>

      <div className="px-6 pb-6 flex flex-col gap-4">

        {/* Steps */}
        <div className="flex flex-col gap-2">

          {/* Origin */}
          {routeOrigin ? (
            <div
              onClick={onClearOrigin}
              className="flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-primary/30 bg-primary/5 cursor-pointer hover:border-error/40 hover:bg-error/5 transition-all"
            >
              <div className="w-8 h-8 rounded-full bg-primary text-on-primary flex items-center justify-center flex-shrink-0">
                <span className="material-symbols-outlined text-sm">trip_origin</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant">From</p>
                <p className="text-sm font-semibold text-on-surface truncate">
                  {routeOriginLabel || `${routeOrigin.lat.toFixed(4)}, ${routeOrigin.lng.toFixed(4)}`}
                </p>
              </div>
              <span className="material-symbols-outlined text-sm text-on-surface-variant opacity-50 flex-shrink-0">edit</span>
            </div>
          ) : (
            <div className={`flex flex-col gap-2 px-4 py-3 rounded-xl border-2 transition-all ${step === 1 ? 'border-primary bg-primary/5' : 'border-outline-variant/20 bg-surface-container'}`}>
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-surface-container-high text-on-surface-variant flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-sm">trip_origin</span>
                </div>
                <div className="flex-1">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant mb-1">From</p>
                  <input
                    type="text"
                    value={originQuery}
                    onChange={handleOriginChange}
                    placeholder="Search or click map…"
                    className="w-full bg-transparent text-sm font-semibold text-on-surface placeholder:text-on-surface-variant/50 outline-none"
                  />
                </div>
              </div>
              {originSuggestions.length > 0 && (
                <ul className="mt-1 -mx-1 flex flex-col divide-y divide-outline-variant/10">
                  {originSuggestions.map((s) => (
                    <li key={s.place_id}>
                      <button
                        onMouseDown={() => selectOrigin(s)}
                        className="w-full text-left px-2 py-2 flex items-center gap-2 hover:bg-primary/5 rounded-lg transition-colors"
                      >
                        <span className="material-symbols-outlined text-sm text-on-surface-variant flex-shrink-0">location_on</span>
                        <span className="text-xs text-on-surface truncate">{s.display_name}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {/* Use my location — only when origin not yet set */}
          {!routeOrigin && (
            <button
              onClick={onUseMyLocation}
              disabled={!userLocation}
              className={`flex items-center gap-3 px-4 py-2.5 rounded-xl border transition-colors text-left ${
                userLocation
                  ? 'bg-primary/5 border-primary/20 hover:bg-primary/10'
                  : 'bg-surface-container border-outline-variant/20 opacity-50 cursor-not-allowed'
              }`}
            >
              <div className={`w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 ${userLocation ? 'bg-primary/10' : 'bg-surface-container-high'}`}>
                <span className={`material-symbols-outlined text-sm ${userLocation ? 'text-primary' : 'text-on-surface-variant'}`}>my_location</span>
              </div>
              <span className={`text-sm font-semibold ${userLocation ? 'text-primary' : 'text-on-surface-variant'}`}>
                {userLocation ? 'Use my current location' : 'Location not available'}
              </span>
            </button>
          )}

          {/* Swap button */}
          {routeOrigin && routeDest && (
            <div className="flex justify-center -my-1">
              <button
                onClick={onSwap}
                className="w-8 h-8 rounded-full bg-surface-container border border-outline-variant/30 flex items-center justify-center hover:bg-primary/10 hover:text-primary hover:border-primary/30 transition-colors z-10"
                aria-label="Swap origin and destination"
              >
                <span className="material-symbols-outlined text-sm">swap_vert</span>
              </button>
            </div>
          )}

          {/* Dest */}
          {routeDest ? (
            <div
              onClick={onClearDest}
              className="flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-primary/30 bg-primary/5 cursor-pointer hover:border-error/40 hover:bg-error/5 transition-all"
            >
              <div className="w-8 h-8 rounded-full bg-primary text-on-primary flex items-center justify-center flex-shrink-0">
                <span className="material-symbols-outlined text-sm">location_on</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant">To</p>
                <p className="text-sm font-semibold text-on-surface truncate">
                  {routeDestLabel || `${routeDest.lat.toFixed(4)}, ${routeDest.lng.toFixed(4)}`}
                </p>
              </div>
              <span className="material-symbols-outlined text-sm text-on-surface-variant opacity-50 flex-shrink-0">edit</span>
            </div>
          ) : (
            <div className={`flex flex-col gap-2 px-4 py-3 rounded-xl border-2 transition-all ${
              step === 2 ? 'border-primary bg-primary/5' : 'border-outline-variant/20 bg-surface-container'
            } ${!routeOrigin ? 'opacity-50 pointer-events-none' : ''}`}>
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-surface-container-high text-on-surface-variant flex items-center justify-center flex-shrink-0">
                  <span className="material-symbols-outlined text-sm">location_on</span>
                </div>
                <div className="flex-1">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant mb-1">To</p>
                  <input
                    type="text"
                    value={destQuery}
                    onChange={handleDestChange}
                    placeholder={routeOrigin ? 'Search or click map…' : 'Set origin first'}
                    disabled={!routeOrigin}
                    className="w-full bg-transparent text-sm font-semibold text-on-surface placeholder:text-on-surface-variant/50 outline-none disabled:cursor-not-allowed"
                  />
                </div>
              </div>
              {destSuggestions.length > 0 && (
                <ul className="mt-1 -mx-1 flex flex-col divide-y divide-outline-variant/10">
                  {destSuggestions.map((s) => (
                    <li key={s.place_id}>
                      <button
                        onMouseDown={() => selectDest(s)}
                        className="w-full text-left px-2 py-2 flex items-center gap-2 hover:bg-primary/5 rounded-lg transition-colors"
                      >
                        <span className="material-symbols-outlined text-sm text-on-surface-variant flex-shrink-0">location_on</span>
                        <span className="text-xs text-on-surface truncate">{s.display_name}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Loading */}
        {loading && (
          <div className="flex items-center gap-3 px-4 py-3 bg-surface-container rounded-xl">
            <span className="material-symbols-outlined text-primary animate-spin">progress_activity</span>
            <p className="text-sm text-on-surface-variant">Finding routes...</p>
          </div>
        )}

        {/* Error */}
        {routeError && (
          <div className="flex items-center gap-3 px-4 py-3 bg-error-container/20 rounded-xl">
            <span className="material-symbols-outlined text-error text-base">error</span>
            <p className="text-sm text-error">{routeError}</p>
          </div>
        )}

        {/* Obstacle warning when no alternative found */}
        {routes && routes.length > 0 && routes[0].hasObstacles && routes.every(r => r.hasObstacles) && (
          <div className="flex items-start gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl">
            <span className="material-symbols-outlined text-amber-600 text-base flex-shrink-0 mt-0.5">warning</span>
            <p className="text-sm text-amber-800">No obstacle-free route could be found. All available routes pass through reported accessibility issues.</p>
          </div>
        )}

        {/* Route cards */}
        {routes && routes.length > 0 && (
          <div className="flex flex-col gap-3">
            <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant">
              {routes.length} Route{routes.length > 1 ? 's' : ''} Found
            </p>
            {routes.map((route, i) => (
              <button
                key={i}
                onClick={() => onSelectRoute(i)}
                className={`text-left px-4 py-4 rounded-xl border-2 transition-all ${
                  activeRouteIndex === i
                    ? 'border-primary bg-primary/5 shadow-sm'
                    : 'border-outline-variant/20 bg-surface-container hover:border-primary/40'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div
                      className="w-3 h-3 rounded-full flex-shrink-0"
                      style={{ background: routeColor(route) }}
                    />
                    <p className="text-sm font-bold text-on-surface">{route.label}</p>
                  </div>
                  {activeRouteIndex === i && (
                    <span className="material-symbols-outlined text-primary text-base">check_circle</span>
                  )}
                </div>
                <div className="flex gap-4 mt-1">
                  <div className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-sm text-on-surface-variant">straighten</span>
                    <span className="text-xs font-semibold text-on-surface">{formatDistance(route.distance)}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-sm text-on-surface-variant">schedule</span>
                    <span className="text-xs font-semibold text-on-surface">{formatDuration(route.duration)}</span>
                  </div>
                  {route.hasObstacles && (
                    <div className="flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-sm text-amber-600">warning</span>
                      <span className="text-xs font-semibold text-amber-600">Obstacles</span>
                    </div>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}

        {/* Hint when waiting for first click */}
        {!routeOrigin && !loading && (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <span className="material-symbols-outlined text-5xl text-primary/30">route</span>
            <p className="text-sm text-on-surface-variant">Search above or click the map to set your starting point</p>
          </div>
        )}

      </div>
      </aside>
    </div>
  )
}

export default RoutePanel
