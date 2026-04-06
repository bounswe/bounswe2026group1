/**
 * RoutePanel
 * Left sidebar shown when route mode is active.
 * Props:
 *  - routeMode: bool
 *  - routeOrigin: {lat, lng} | null
 *  - routeDest: {lat, lng} | null
 *  - routes: Array<{coords, label, distance, duration, hasObstacles}> | null
 *  - activeRouteIndex: number
 *  - routeError: string
 *  - loading: bool
 *  - onSelectRoute: (index) => void
 *  - onReset: () => void
 */
function RoutePanel({
  routeOrigin,
  routeDest,
  routes,
  activeRouteIndex,
  routeError,
  loading,
  userLocation,
  onUseMyLocation,
  onSelectRoute,
  onReset,
}) {
  function routeColor(route) {
    if (route.label?.includes('Accessible')) return '#1565C0'  // deep blue
    if (route.label?.includes('Wheelchair')) return '#6A1B9A'  // deep purple
    if (route.label?.includes('Ramp'))       return '#00695C'  // deep teal
    return route.hasObstacles ? '#E65100' : '#2E7D32'          // orange or green
  }

  function formatDistance(m) {
    return m >= 1000 ? `${(m / 1000).toFixed(2)} km` : `${Math.round(m)} m`
  }

  function formatDuration(s) {
    const mins = Math.ceil(s / 60)
    return mins >= 60 ? `${Math.floor(mins / 60)}h ${mins % 60}m` : `${mins} min`
  }

  const step = !routeOrigin ? 1 : !routeDest ? 2 : 3

  return (
    <aside className="
      w-[360px] flex-shrink-0 h-full
      bg-surface-container-low
      border-r border-outline-variant/10
      flex flex-col overflow-y-auto z-10
    ">
      {/* Header */}
      <div className="px-6 pt-6 pb-4 flex items-center justify-between flex-shrink-0">
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
          <div className={`flex items-center gap-3 px-4 py-3 rounded-xl border-2 transition-all ${
            step === 1 ? 'border-primary bg-primary/5' : routeOrigin ? 'border-primary/30 bg-primary/5' : 'border-outline-variant/20 bg-surface-container'
          }`}>
            <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
              routeOrigin ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-on-surface-variant'
            }`}>
              <span className="material-symbols-outlined text-sm">trip_origin</span>
            </div>
            <div>
              <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant">From</p>
              <p className="text-sm font-semibold text-on-surface">
                {routeOrigin
                  ? `${routeOrigin.lat.toFixed(4)}, ${routeOrigin.lng.toFixed(4)}`
                  : 'Click on the map'}
              </p>
            </div>
          </div>

          {/* Use my location shortcut — only when origin not yet set */}
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

          {/* Dest */}
          <div className={`flex items-center gap-3 px-4 py-3 rounded-xl border-2 transition-all ${
            step === 2 ? 'border-primary bg-primary/5' : routeDest ? 'border-primary/30 bg-primary/5' : 'border-outline-variant/20 bg-surface-container'
          }`}>
            <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
              routeDest ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-on-surface-variant'
            }`}>
              <span className="material-symbols-outlined text-sm">location_on</span>
            </div>
            <div>
              <p className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant">To</p>
              <p className="text-sm font-semibold text-on-surface">
                {routeDest
                  ? `${routeDest.lat.toFixed(4)}, ${routeDest.lng.toFixed(4)}`
                  : routeOrigin ? 'Click on the map' : 'Set origin first'}
              </p>
            </div>
          </div>
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
            <p className="text-sm text-on-surface-variant">Click anywhere on the map to set your starting point</p>
          </div>
        )}

      </div>
    </aside>
  )
}

export default RoutePanel
