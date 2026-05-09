import { useMemo, useState, useCallback, useEffect, useRef } from 'react'
import { MapContainer, TileLayer, Marker, Circle, Popup, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import Navbar from '../components/Navbar.jsx'
import ReportCard from '../components/ReportCard.jsx'
import ReportPanel from '../components/ReportPanel.jsx'
import Toast from '../components/Toast.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { getReportsFeed, mapReport } from '../services/reportService.js'
import { haversineKm } from '../utils/geo.js'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'

const DEFAULT_CENTER = { lat: 41.0683, lng: 29.0505 }
const DEFAULT_ZOOM = 13
const FLY_ZOOM = 16
const GEO_OPTIONS = { enableHighAccuracy: true, timeout: 25_000, maximumAge: 0 }
/** Map height inside the constrained feed grid (below the toolbar). Detail mode uses Home-style full bleed. */
const FEED_EMBEDDED_MAP_H_CLASS = 'h-[min(41vh,360px)]'

const LOCATION_SERVICES_MSG = 'Location services are not enabled.'
const BLANK_MESSAGE =
  'Please allow location access or choose a location on the map.'

const BTN_PRIMARY =
  'inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-bold text-on-primary shadow-sm transition hover:bg-primary-dim focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary font-headline disabled:pointer-events-none disabled:opacity-40'

const BTN_OUTLINE =
  'inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl border border-outline-variant bg-white px-4 text-sm font-bold text-on-surface shadow-sm transition hover:bg-surface-container-high focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary font-headline disabled:pointer-events-none disabled:opacity-40'

const BTN_ICON_CLOSE =
  'inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-outline-variant bg-surface-container-low text-on-surface shadow-sm transition hover:bg-surface-container-high focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** Extra control on feed map detail — matches floating map toolbar tokens (does not alter ReportPanel). */
const BTN_BACK_MAP_LAYER =
  'inline-flex items-center gap-2 rounded-xl border border-outline-variant/25 bg-white/90 px-3 py-2 text-sm font-semibold text-on-surface shadow-md backdrop-blur-sm transition hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** Page size for spatial feed pagination (must match backend Pageable). */
const FEED_PAGE_SIZE = 10

function makeMarkerIcon(status, objectType) {
  const cfg = OBJECT_TYPE_MAP[objectType] ?? { icon: 'warning', markerColor: '#767777' }
  const borderColor = status === 'verified' ? '#176a21' : cfg.markerColor
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
          color:${borderColor};
          font-variation-settings:'FILL' 1;
          line-height:1;
        ">${cfg.icon}</span>
      </div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -24],
  })
}

function MapInvalidateOnOpen() {
  const map = useMap()
  useEffect(() => {
    const t = window.setTimeout(() => map.invalidateSize(), 200)
    return () => window.clearTimeout(t)
  }, [map])
  return null
}

function MapClickPanTo() {
  const map = useMap()
  useMapEvents({
    click(e) {
      map.panTo(e.latlng)
    },
  })
  return null
}

/**
 * Tracks map viewport center on pan/zoom: circle sticks to geographic center under a fixed HUD pin overlay.
 */
function MapViewportCenterCircle({ radiusKm, onCenterDebounced }) {
  const map = useMap()
  const [centerLatLng, setCenterLatLng] = useState(() => {
    const c = map.getCenter()
    return { lat: c.lat, lng: c.lng }
  })

  useEffect(() => {
    let debounceTimer = null
    function sync() {
      const c = map.getCenter()
      const ll = { lat: c.lat, lng: c.lng }
      setCenterLatLng(ll)
      if (debounceTimer) window.clearTimeout(debounceTimer)
      debounceTimer = window.setTimeout(() => {
        debounceTimer = null
        onCenterDebounced(ll)
      }, 280)
    }
    sync()
    map.on('moveend', sync)
    map.on('zoomend', sync)
    return () => {
      map.off('moveend', sync)
      map.off('zoomend', sync)
      if (debounceTimer) window.clearTimeout(debounceTimer)
    }
  }, [map, onCenterDebounced])

  if (radiusKm <= 0) return null
  return (
    <Circle
      center={[centerLatLng.lat, centerLatLng.lng]}
      radius={radiusKm * 1000}
      pathOptions={{
        color: '#176a21',
        fillColor: '#176a21',
        fillOpacity: 0.08,
        weight: 2,
      }}
    />
  )
}

function MapFlyToReport({ report }) {
  const map = useMap()
  useEffect(() => {
    if (!report?.id) return
    map.flyTo([report.latitude, report.longitude], FLY_ZOOM, { duration: 0.75 })
  }, [map, report?.id, report?.latitude, report?.longitude])
  return null
}

/** Main feed map: markers + radius + fly to selection */
function FeedBrowseMap({
  activeCoords,
  radiusKm,
  reports,
  selectedReport,
  onMarkerSelect,
  markersRef,
  /** Same footprint as Home map (`/`) when a report is open. */
  viewportFill = false,
}) {
  const mapCenter = [activeCoords.lat, activeCoords.lng]
  const mapClass = viewportFill
    ? 'relative z-0 h-full w-full min-h-0'
    : `w-full ${FEED_EMBEDDED_MAP_H_CLASS} min-h-[200px] lg:h-full lg:min-h-0`

  return (
    <MapContainer
      center={mapCenter}
      zoom={DEFAULT_ZOOM}
      zoomControl
      className={mapClass}
      style={viewportFill ? { height: '100%', width: '100%' } : { height: '100%' }}
      scrollWheelZoom
    >
      <MapInvalidateOnOpen />
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      />
      {radiusKm > 0 && activeCoords && (
        <Circle
          center={[activeCoords.lat, activeCoords.lng]}
          radius={radiusKm * 1000}
          pathOptions={{
            color: '#176a21',
            fillColor: '#176a21',
            fillOpacity: 0.06,
            weight: 1,
            dashArray: '6 10',
          }}
        />
      )}
      <MapFlyToReport report={selectedReport} />
      {reports.map((report) => (
        <Marker
          key={report.id}
          position={[report.latitude, report.longitude]}
          icon={makeMarkerIcon(report.status, report.primaryObjectType)}
          eventHandlers={{
            add(e) {
              markersRef.current[report.id] = e.target
            },
            remove() {
              delete markersRef.current[report.id]
            },
            click() {
              onMarkerSelect({ ...report })
            },
          }}
        >
          <Popup closeButton>
            <div className="min-w-[210px] max-w-[260px] pr-1">
              <p className="font-headline text-sm font-bold leading-snug text-on-surface">{report.title}</p>
              {report.description && (
                <p className="mt-1 text-xs leading-relaxed text-on-surface-variant line-clamp-3">{report.description}</p>
              )}
              <p className="mt-2 text-[11px] font-semibold uppercase tracking-wide text-primary">
                Open below for details
              </p>
            </div>
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  )
}

function CenterHudPinOverlay() {
  return (
    <div
      className="pointer-events-none absolute inset-0 z-[650] flex items-center justify-center"
      aria-hidden
    >
      <div className="-mt-12 flex flex-col items-center drop-shadow-lg">
        <span
          className="material-symbols-outlined text-[3.5rem] text-primary"
          style={{ fontVariationSettings: "'FILL' 1" }}
        >
          location_on
        </span>
      </div>
    </div>
  )
}

function LocationMapModal({
  open,
  onClose,
  initialCenter,
  radiusKm,
  mapKey,
  onCenterDebounced,
  onUseMyLocation,
}) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-[2000]">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" aria-hidden />
      <div className="relative z-[2001] flex min-h-full items-center justify-center p-4 pointer-events-none sm:p-6">
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="feed-map-title"
          className="
            pointer-events-auto flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden
            rounded-[1.5rem] border border-outline-variant/20 bg-surface-container-low shadow-2xl
          "
        >
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant/15 px-4 py-3 sm:px-5">
            <h2 id="feed-map-title" className="font-headline text-lg font-bold text-on-surface">
              Choose location
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" className={BTN_OUTLINE} onClick={onUseMyLocation}>
                <span className="material-symbols-outlined text-[20px]">my_location</span>
                Use my current location
              </button>
              <button type="button" className={BTN_ICON_CLOSE} onClick={onClose} aria-label="Close map">
                <span className="material-symbols-outlined text-xl">close</span>
              </button>
            </div>
          </div>
          <p className="border-b border-outline-variant/10 px-4 py-2 text-xs text-on-surface-variant sm:px-5">
            Pan and zoom—the guide marks the viewport center used for nearby results. Close with ✕ only.
          </p>
          <div className="relative min-h-[min(55vh,420px)] w-full flex-1">
            <MapContainer
              key={mapKey}
              center={[initialCenter.lat, initialCenter.lng]}
              zoom={DEFAULT_ZOOM}
              zoomControl
              className="h-[min(55vh,420px)] w-full"
              style={{ height: 'min(55vh,420px)' }}
              scrollWheelZoom
            >
              <MapInvalidateOnOpen />
              <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              />
              <MapClickPanTo />
              <MapViewportCenterCircle radiusKm={radiusKm} onCenterDebounced={onCenterDebounced} />
            </MapContainer>
            <CenterHudPinOverlay />
          </div>
        </div>
      </div>
    </div>
  )
}

function parseRadiusKm(raw) {
  const n = Number.parseFloat(String(raw).replace(',', '.'))
  if (Number.isNaN(n) || n <= 0) return null
  return Math.min(5000, n)
}

function Feed() {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  const suppressInitialGeo = useRef(false)
  const markersRef = useRef({})

  const [activeCoords, setActiveCoords] = useState(null)
  const [geoState, setGeoState] = useState('pending')
  const [radiusInput, setRadiusInput] = useState('1')
  const [mapModalOpen, setMapModalOpen] = useState(false)
  const [mapKey, setMapKey] = useState(0)
  const [selectedReport, setSelectedReport] = useState(null)
  const [userVotes, setUserVotes] = useState({})
  const [toast, setToast] = useState(null)

  const feedScrollRef = useRef(null)
  const loadMoreSentinelRef = useRef(null)

  const dismissToast = useCallback(() => setToast(null), [])

  const radiusKm = useMemo(() => parseRadiusKm(radiusInput) ?? 1, [radiusInput])

  const showLocationServicesAlert = useCallback(() => {
    setToast({ message: LOCATION_SERVICES_MSG, type: 'error' })
  }, [])

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setGeoState('unsupported')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (suppressInitialGeo.current) return
        const c = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        setActiveCoords(c)
        setGeoState('granted')
      },
      (err) => {
        if (err.code === 1) setGeoState('denied')
        else if (err.code === 2) setGeoState('unavailable')
        else if (err.code === 3) setGeoState('timeout')
        else setGeoState('unavailable')
      },
      GEO_OPTIONS
    )
  }, [])

  const hasLocation = activeCoords != null

  const {
    data: feedData,
    isPending: feedInitialPending,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['reports-feed', activeCoords?.lat, activeCoords?.lng, radiusKm, token ?? ''],
    queryFn: ({ pageParam }) =>
      getReportsFeed(
        {
          latitude: activeCoords.lat,
          longitude: activeCoords.lng,
          radiusInKm: radiusKm,
          page: pageParam,
          size: FEED_PAGE_SIZE,
        },
        token
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      if (!lastPage || lastPage.last) return undefined
      const n = lastPage.number
      return typeof n === 'number' ? n + 1 : undefined
    },
    enabled: hasLocation,
  })

  useEffect(() => {
    const root = feedScrollRef.current
    const sentinel = loadMoreSentinelRef.current
    if (!root || !sentinel || !hasNextPage) return undefined

    const observer = new IntersectionObserver(
      (entries) => {
        const hit = entries.some((e) => e.isIntersecting)
        if (hit) fetchNextPage()
      },
      { root, rootMargin: '140px', threshold: 0 }
    )

    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [fetchNextPage, hasNextPage, feedData?.pages?.length])

  const viewportCenterCommit = useCallback((ll) => {
    suppressInitialGeo.current = true
    setActiveCoords(ll)
  }, [])

  const openMapPicker = useCallback(() => {
    setMapKey((k) => k + 1)
    setMapModalOpen(true)
  }, [])

  const requestMyLocation = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setGeoState('unsupported')
      showLocationServicesAlert()
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        suppressInitialGeo.current = true
        const c = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        setActiveCoords(c)
        setGeoState('granted')
        // Remount picker map so its center matches GPS; otherwise debounced
        // MapViewportCenterCircle would briefly overwrite coords with the old center.
        setMapKey((k) => k + 1)
        queueMicrotask(() => {
          queryClient.invalidateQueries({ queryKey: ['reports-feed'] })
        })
      },
      (err) => {
        if (err.code === 1) setGeoState('denied')
        else if (err.code === 2) setGeoState('unavailable')
        else if (err.code === 3) setGeoState('timeout')
        else setGeoState('unavailable')
        showLocationServicesAlert()
      },
      GEO_OPTIONS
    )
  }, [queryClient, showLocationServicesAlert])

  const handleCloseModal = useCallback(() => {
    setMapModalOpen(false)
  }, [])

  const reports = useMemo(() => {
    const pages = feedData?.pages
    if (!pages?.length) return []
    return pages.flatMap((page) => {
      const raw = page?.content
      if (!Array.isArray(raw)) return []
      return raw.map(mapReport)
    })
  }, [feedData])

  const rows = useMemo(() => {
    if (!activeCoords || !reports.length) return []
    return reports.map((r) => ({
      report: r,
      distanceKm: haversineKm(activeCoords.lat, activeCoords.lng, r.latitude, r.longitude),
    }))
  }, [reports, activeCoords])

  const handleSelectReportFromFeedOrMap = useCallback((report) => {
    setSelectedReport(report)
  }, [])

  const handleBackToList = useCallback(() => {
    setSelectedReport(null)
  }, [])

  const handleVoteUpdate = useCallback(
    (updatedReport) => {
      setSelectedReport(updatedReport)
      queryClient.invalidateQueries({ queryKey: ['reports-feed'] })
    },
    [queryClient]
  )

  useEffect(() => {
    if (!selectedReport) return
    const id = selectedReport.id
    const timer = window.setTimeout(() => {
      try {
        markersRef.current[id]?.openPopup?.()
      } catch {
        /* ignore */
      }
    }, 550)
    return () => window.clearTimeout(timer)
  }, [selectedReport])

  const showBlankState = !hasLocation
  const showGeoPending = geoState === 'pending' && !hasLocation

  const toolbar = (
    <div className="border-b border-outline-variant/15 bg-surface-container-low/80 py-4 backdrop-blur-sm">
      <div className="mb-4">
        <h1 className="font-headline text-2xl font-extrabold tracking-tight text-on-surface md:text-3xl">
          Community feed
        </h1>
      </div>
      <div className="flex flex-wrap items-end gap-x-6 gap-y-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2 sm:gap-3">
          <button type="button" className={BTN_PRIMARY} onClick={openMapPicker}>
            <span className="material-symbols-outlined text-[20px]">map</span>
            Choose location from map
          </button>
          <button type="button" className={BTN_OUTLINE} onClick={requestMyLocation}>
            <span className="material-symbols-outlined text-[20px]">my_location</span>
            Use my current location
          </button>
        </div>
        <div className="flex items-center gap-2 border-outline-variant pt-3 sm:border-t-0 sm:pt-0 lg:border-l lg:pl-6">
          <label
            htmlFor="feed-radius-km"
            className="mb-px whitespace-nowrap text-sm font-semibold leading-none text-on-surface-variant"
          >
            Radius (km)
          </label>
          <input
            id="feed-radius-km"
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={radiusInput}
            onChange={(e) => setRadiusInput(e.target.value)}
            placeholder="1"
            disabled={!hasLocation}
            className="
              h-11 w-[5.75rem] rounded-xl border border-outline-variant bg-white px-3 text-center text-sm font-semibold text-on-surface
              shadow-sm outline-none transition
              focus:border-primary focus:ring-2 focus:ring-primary/15
              disabled:cursor-not-allowed disabled:bg-surface-container-high disabled:opacity-50
            "
          />
        </div>
      </div>
    </div>
  )

  /** Horizontal inset + max width for list/empty states only; detail matches Home (`/`) full bleed. */
  const pageInset = 'px-4 md:px-8 lg:px-16'
  const pageInner = 'mx-auto flex h-full min-h-0 w-full max-w-7xl flex-col'

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-background font-body">
      <Navbar />

      {selectedReport ? (
        <div className="relative flex min-h-0 flex-1 overflow-hidden">
          <main className="relative min-h-0 flex-1">
            <FeedBrowseMap
              viewportFill
              activeCoords={activeCoords}
              radiusKm={radiusKm}
              reports={reports}
              selectedReport={selectedReport}
              onMarkerSelect={handleSelectReportFromFeedOrMap}
              markersRef={markersRef}
            />
            <button
              type="button"
              aria-label="Back to feed list"
              onClick={handleBackToList}
              className={`${BTN_BACK_MAP_LAYER} absolute left-3 top-24 z-[1260] sm:left-10`}
            >
              <span className="material-symbols-outlined text-xl">arrow_back</span>
              Back to feed
            </button>
          </main>
          <ReportPanel
            key={selectedReport.id}
            report={selectedReport}
            userVote={userVotes[selectedReport.id] ?? null}
            onVoteChange={(vote) => setUserVotes((prev) => ({ ...prev, [selectedReport.id]: vote }))}
            onClose={handleBackToList}
            onVoteUpdate={handleVoteUpdate}
          />
        </div>
      ) : (
        <div className={`min-h-0 flex-1 ${pageInset}`}>
          <div className={pageInner}>
            {showBlankState ? (
              <main className="relative flex min-h-0 flex-1 flex-col overflow-auto">
                <div className="py-6">{toolbar}</div>
                <div className="mx-auto w-full max-w-4xl flex-1 pb-12">
                  <div className="flex flex-col items-center justify-center rounded-[2rem] border border-outline-variant/20 bg-white/60 py-20 text-center shadow-sm backdrop-blur-md">
                    {showGeoPending ? (
                      <span className="material-symbols-outlined animate-pulse text-6xl text-primary/50">near_me</span>
                    ) : (
                      <span className="material-symbols-outlined text-6xl text-on-surface-variant/30">map_search</span>
                    )}
                    <p className="mx-auto mt-6 max-w-md text-base leading-relaxed text-on-surface-variant">
                      {BLANK_MESSAGE}
                    </p>
                  </div>
                </div>
              </main>
            ) : (
              <main className="flex min-h-0 flex-1 flex-col overflow-hidden">
                {toolbar}
                <div ref={feedScrollRef} className="min-h-0 flex-1 overflow-y-auto overscroll-contain py-5">
                    {!showBlankState && hasLocation && feedInitialPending && (
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-3">
                        {[1, 2, 3, 4].map((k) => (
                          <div
                            key={k}
                            className="h-36 animate-pulse rounded-lg border border-outline-variant/20 bg-white/50"
                          />
                        ))}
                      </div>
                    )}

                    {!showBlankState && hasLocation && !feedInitialPending && rows.length === 0 && (
                      <div className="flex flex-col items-center justify-center py-16 text-center">
                        <span className="material-symbols-outlined text-5xl text-on-surface-variant/35">map_search</span>
                        <p className="mt-3 font-headline text-lg font-bold text-on-surface">No reports in this area</p>
                      </div>
                    )}

                    {!showBlankState && hasLocation && !feedInitialPending && rows.length > 0 && (
                      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 sm:gap-3">
                        {rows.map(({ report, distanceKm }) => (
                          <ReportCard
                            key={report.id}
                            report={report}
                            distanceKm={distanceKm}
                            showProximity
                            onCardClick={() => handleSelectReportFromFeedOrMap({ ...report })}
                          />
                        ))}
                      </div>
                    )}

                    {hasLocation && (hasNextPage === true || isFetchingNextPage) && (
                      <div
                        ref={loadMoreSentinelRef}
                        className="flex min-h-8 w-full items-center justify-center py-4"
                        aria-hidden
                      >
                        {isFetchingNextPage && (
                          <span
                            className="material-symbols-outlined animate-spin text-3xl text-primary"
                            aria-label="Loading more"
                          >
                            progress_activity
                          </span>
                        )}
                      </div>
                    )}
                </div>
              </main>
            )}
          </div>
        </div>
      )}

      <LocationMapModal
        open={mapModalOpen}
        onClose={handleCloseModal}
        initialCenter={activeCoords ?? DEFAULT_CENTER}
        radiusKm={radiusKm}
        mapKey={mapKey}
        onCenterDebounced={viewportCenterCommit}
        onUseMyLocation={requestMyLocation}
      />

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          duration={toast.type === 'error' ? 5000 : 3000}
          onDismiss={dismissToast}
        />
      )}
    </div>
  )
}

export default Feed
