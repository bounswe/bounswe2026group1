import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import Navbar from '../components/Navbar.jsx'
import FeedLocationPickerModal from '../components/FeedLocationPickerModal.jsx'
import { useTheme } from '../context/ThemeContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { getReportFeed, mapReport } from '../services/reportService.js'

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

const DEFAULT_CENTER = { lat: 41.0683, lng: 29.0505 }

/** Status pills — verified uses app primary green (same family as buttons / map) */
const STATUS_STYLES = {
  unverified:
    'bg-yellow-100 text-yellow-900 dark:bg-amber-950/60 dark:text-amber-100',
  verified: 'bg-primary-container text-on-primary-container',
  rejected: 'bg-red-100 text-red-900 dark:bg-red-950/60 dark:text-red-100',
  fixed: 'bg-blue-100 text-blue-900 dark:bg-blue-950/60 dark:text-blue-100',
}

const STATUS_LABELS = {
  unverified: 'Unverified',
  verified: 'Validated',
  rejected: 'Rejected',
  fixed: 'Fixed',
}

/** Chip colors aligned with Navbar “Mapcess” wordmark (`text-primary`) */
export function feedFilterChipClass(active) {
  return [
    'inline-flex items-center justify-center px-4 py-2 rounded-2xl text-sm font-semibold border transition-colors',
    'focus:outline-none focus-visible:ring-2 focus-visible:ring-primary',
    active
      ? 'bg-primary text-on-primary border-primary shadow-sm'
      : 'bg-surface-container-highest text-primary border-primary/30 hover:bg-primary/10 hover:border-primary/45',
  ].join(' ')
}

function useDebouncedValue(value, delay) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

function clampRadiusKm(km) {
  const n = Number(km)
  if (!Number.isFinite(n)) return 5
  return Math.min(500, Math.max(0.1, n))
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

function formatDistanceKm(center, lat, lon) {
  if (!center || lat == null || lon == null) return null
  const la = typeof lat === 'number' ? lat : Number(lat)
  const lo = typeof lon === 'number' ? lon : Number(lon)
  if (!Number.isFinite(la) || !Number.isFinite(lo)) return null
  const km = haversineKm(center.lat, center.lng, la, lo)
  if (!Number.isFinite(km)) return null
  if (km < 1) return `${Math.round(km * 1000)} m`
  return `${km.toFixed(1)} km`
}

function envLabel(env) {
  if (env === 'INDOOR') return 'Indoor'
  if (env === 'OUTDOOR') return 'Outdoor'
  return null
}

function typeLabel(t) {
  if (t === 'FEATURE') return 'Feature'
  if (t === 'OBSTACLE') return 'Obstacle'
  return null
}

export default function Feed() {
  const { resolved: themeResolved } = useTheme()
  const isDark = themeResolved === 'dark'
  const tileUrl = isDark ? TILE_DARK : TILE_LIGHT
  const tileAttr = isDark ? TILE_ATTR_DARK : TILE_ATTR_LIGHT
  const { token } = useAuth()

  const [pickerOpen, setPickerOpen] = useState(false)
  const [feedCenterDraft, setFeedCenterDraft] = useState(null)
  const feedCenter = useDebouncedValue(feedCenterDraft, 400)
  const [radiusKmInput, setRadiusKmInput] = useState('5')
  const radiusKm = clampRadiusKm(radiusKmInput)

  const [reportTypeFilter, setReportTypeFilter] = useState('ALL')
  const [environmentFilter, setEnvironmentFilter] = useState('ALL')

  const [locateHint, setLocateHint] = useState(null)

  useEffect(() => {
    if (!locateHint) return
    const t = setTimeout(() => setLocateHint(null), 4000)
    return () => clearTimeout(t)
  }, [locateHint])

  const queryParams = useMemo(() => {
    const lat = feedCenter?.lat
    const lng = feedCenter?.lng
    const hasValidCenter =
      feedCenter != null &&
      Number.isFinite(Number(lat)) &&
      Number.isFinite(Number(lng))
    return {
      reportType: reportTypeFilter,
      environment: environmentFilter,
      latitude: hasValidCenter ? Number(lat) : undefined,
      longitude: hasValidCenter ? Number(lng) : undefined,
      radiusInKm: hasValidCenter ? radiusKm : undefined,
    }
  }, [reportTypeFilter, environmentFilter, feedCenter, radiusKm])

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isPending,
    isError,
    error,
  } = useInfiniteQuery({
    queryKey: ['reportFeed', queryParams],
    queryFn: ({ pageParam, signal }) =>
      getReportFeed(
        {
          page: pageParam,
          size: 20,
          reportType: queryParams.reportType,
          environment: queryParams.environment,
          latitude: queryParams.latitude,
          longitude: queryParams.longitude,
          radiusInKm: queryParams.radiusInKm,
        },
        token,
        { signal }
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
  })

  const reports = useMemo(() => {
    if (!data?.pages) return []
    return data.pages.flatMap((p) => (p.content || []).map(mapReport))
  }, [data])

  const modalCenter = feedCenter ?? DEFAULT_CENTER
  const referenceForDistance = feedCenterDraft ?? feedCenter

  const reportsSorted = useMemo(() => {
    const ref = referenceForDistance
    if (!ref) return reports
    return [...reports].sort((a, b) => {
      const da = haversineKm(ref.lat, ref.lng, Number(a.latitude), Number(a.longitude))
      const db = haversineKm(ref.lat, ref.lng, Number(b.latitude), Number(b.longitude))
      const fa = Number.isFinite(da) ? da : Number.POSITIVE_INFINITY
      const fb = Number.isFinite(db) ? db : Number.POSITIVE_INFINITY
      return fa - fb
    })
  }, [reports, referenceForDistance])

  const loadMoreRef = useRef(null)
  const onIntersect = useCallback(
    (entries) => {
      const [entry] = entries
      if (entry?.isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage()
      }
    },
    [hasNextPage, isFetchingNextPage, fetchNextPage]
  )

  useEffect(() => {
    const el = loadMoreRef.current
    if (!el) return
    const obs = new IntersectionObserver(onIntersect, { rootMargin: '160px' })
    obs.observe(el)
    return () => obs.disconnect()
  }, [onIntersect])

  const hasActiveFilter =
    reportTypeFilter !== 'ALL' || environmentFilter !== 'ALL'

  const emptyMessage = useMemo(() => {
    if (reportsSorted.length > 0 || isPending || isError) return null
    if (hasActiveFilter) return 'No reports match your filters.'
    if (feedCenter != null) return 'No reports within this search radius.'
    return 'No reports yet.'
  }, [reportsSorted.length, isPending, isError, hasActiveFilter, feedCenter])

  function handleUseCurrentLocation() {
    if (!navigator.geolocation) {
      setLocateHint('Location is not available on this device.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setFeedCenterDraft({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        })
      },
      () =>
        setLocateHint(
          'Could not get your position. Allow location access or set a point on the map.'
        ),
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 60_000 }
    )
  }

  return (
    <div className="min-h-screen flex flex-col bg-background font-body">
      <Navbar />

      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 md:px-8 py-8 md:py-12 flex flex-col gap-8">
        <header>
          <h1 className="text-4xl md:text-5xl font-extrabold font-headline text-on-surface tracking-tight">
            Community feed
          </h1>
        </header>

        <section className="rounded-2xl border border-outline-variant/20 bg-white dark:bg-surface-container-lowest shadow-sm p-6 md:p-8 flex flex-col gap-6">
          {/* Filters + radius: one row, aligned */}
          <div className="flex flex-wrap items-end gap-x-6 gap-y-4 justify-between">
            <div className="flex flex-wrap items-end gap-x-8 gap-y-4 flex-1 min-w-0">
              <div className="flex flex-col gap-2 min-w-0">
                <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  Report type
                </span>
                <div className="flex flex-wrap gap-2">
                  {[
                    { id: 'ALL', label: 'All' },
                    { id: 'OBSTACLE', label: 'Obstacle' },
                    { id: 'FEATURE', label: 'Feature' },
                  ].map(({ id, label }) => (
                    <button
                      key={id}
                      type="button"
                      aria-pressed={reportTypeFilter === id}
                      aria-label={`Report type: ${label}`}
                      className={feedFilterChipClass(reportTypeFilter === id)}
                      onClick={() => setReportTypeFilter(id)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex flex-col gap-2 min-w-0">
                <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  Environment
                </span>
                <div className="flex flex-wrap gap-2">
                  {[
                    { id: 'ALL', label: 'All' },
                    { id: 'OUTDOOR', label: 'Outdoor' },
                    { id: 'INDOOR', label: 'Indoor' },
                  ].map(({ id, label }) => (
                    <button
                      key={id}
                      type="button"
                      aria-pressed={environmentFilter === id}
                      aria-label={`Environment: ${label}`}
                      className={feedFilterChipClass(environmentFilter === id)}
                      onClick={() => setEnvironmentFilter(id)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="shrink-0 w-full sm:w-auto">
              <label className="flex flex-col gap-1">
                <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  Search radius (km)
                </span>
                <input
                  type="number"
                  min={0.1}
                  max={500}
                  step={0.1}
                  value={radiusKmInput}
                  onChange={(e) => setRadiusKmInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'ArrowUp' || e.key === 'ArrowDown') e.preventDefault()
                  }}
                  className="w-full sm:w-[4.75rem] rounded-xl border border-outline-variant bg-background px-2 py-2 text-sm text-on-surface font-semibold text-center [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                />
              </label>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => setPickerOpen(true)}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl bg-primary-container text-on-primary-container font-bold text-sm border border-primary/30 shadow-sm hover:opacity-90 transition-opacity"
            >
              <span className="material-symbols-outlined text-lg">map</span>
              Choose on map
            </button>
            <button
              type="button"
              onClick={handleUseCurrentLocation}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl border border-outline-variant/30 bg-white dark:bg-transparent font-bold text-sm text-on-surface hover:bg-surface-container-low transition-colors shadow-sm"
            >
              <span className="material-symbols-outlined text-lg">my_location</span>
              Use current location
            </button>
          </div>

          {feedCenterDraft != null && (
            <p className="text-sm text-on-surface-variant">
              Reference point: {feedCenterDraft.lat.toFixed(4)}, {feedCenterDraft.lng.toFixed(4)}
            </p>
          )}
        </section>

        {locateHint && (
          <p role="status" className="text-sm text-on-surface-variant">
            {locateHint}
          </p>
        )}

        {isError && (
          <p role="alert" className="text-sm text-error font-medium">
            {error?.message || 'Could not load the feed.'}
          </p>
        )}

        <ul className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-5 list-none p-0 m-0">
          {reportsSorted.map((report) => {
            const distLabel = formatDistanceKm(
              referenceForDistance,
              report.latitude,
              report.longitude
            )
            const env = envLabel(report.environment)
            const rtype = typeLabel(report.reportType)

            return (
              <li key={report.id} className="min-w-0">
                <Link
                  to={`/?report=${report.id}&from=feed`}
                  className="flex flex-col h-full rounded-2xl border border-outline-variant/20 bg-white dark:bg-surface-container-lowest shadow-sm p-4 hover:shadow-md transition-shadow group"
                >
                  <div className="relative aspect-[4/3] w-full rounded-lg overflow-hidden bg-surface-container-high mb-3">
                    {report.image ? (
                      <img
                        src={report.image}
                        alt=""
                        className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-on-surface-variant">
                        <span className="material-symbols-outlined text-5xl opacity-40">image</span>
                      </div>
                    )}
                    {distLabel && (
                      <div className="absolute left-2 top-2 z-10 max-w-[calc(100%-1rem)] pointer-events-none">
                        <span className="inline-flex items-center gap-0.5 rounded-full bg-purple-100 px-1.5 py-0.5 text-[10px] font-semibold leading-tight text-purple-800 shadow-sm ring-1 ring-white/90 dark:bg-purple-950/65 dark:text-purple-200 dark:ring-black/40">
                          <span
                            className="material-symbols-outlined text-[12px] shrink-0 text-purple-700 dark:text-purple-300"
                            style={{ fontVariationSettings: "'FILL' 1" }}
                          >
                            near_me
                          </span>
                          <span className="truncate">{distLabel}</span>
                        </span>
                      </div>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-1.5 mb-2">
                    <span
                      className={`text-xs font-bold px-3 py-1 rounded-full ${
                        STATUS_STYLES[report.status] ?? 'bg-surface-container-high text-on-surface'
                      }`}
                    >
                      {STATUS_LABELS[report.status] ?? report.status}
                    </span>
                    {rtype && (
                      <span className="text-xs font-bold px-3 py-1 rounded-full border border-primary/25 bg-primary/8 text-primary dark:bg-primary/15 dark:text-primary">
                        {rtype}
                      </span>
                    )}
                    {env && (
                      <span className="text-xs font-bold px-3 py-1 rounded-full border border-outline-variant/35 bg-surface-container-lowest text-on-surface-variant">
                        {env}
                      </span>
                    )}
                  </div>

                  <h2 className="text-lg md:text-xl font-bold font-headline text-on-surface leading-snug line-clamp-2 mb-1.5">
                    {report.title}
                  </h2>

                  {report.description ? (
                    <p className="text-sm text-on-surface-variant line-clamp-2 mb-3 flex-1">
                      {report.description}
                    </p>
                  ) : (
                    <p className="text-sm text-on-surface-variant/70 italic mb-3 flex-1">
                      No description
                    </p>
                  )}

                  <div className="mt-auto pt-2 border-t border-outline-variant/20 flex flex-col gap-2">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-on-surface-variant">
                      <span className="inline-flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-[18px] text-on-surface-variant">
                          calendar_today
                        </span>
                        {report.date}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                      <span className="inline-flex items-center gap-1.5 text-on-surface-variant truncate max-w-[55%] min-w-0">
                        <span
                          className="material-symbols-outlined text-[16px] text-primary shrink-0"
                          style={{ fontVariationSettings: "'FILL' 1" }}
                          aria-hidden
                        >
                          location_on
                        </span>
                        <span className="truncate font-medium text-on-surface">{report.location}</span>
                      </span>
                      <span className="inline-flex items-center gap-3 shrink-0 text-sm font-semibold not-italic">
                        <span
                          className="inline-flex items-center gap-1 text-primary"
                          title="Agree"
                        >
                          <span
                            className="material-symbols-outlined text-[16px]"
                            style={{ fontVariationSettings: "'FILL' 1" }}
                          >
                            thumb_up
                          </span>
                          {report.agrees ?? 0}
                        </span>
                        <span
                          className="inline-flex items-center gap-1 text-error"
                          title="Disagree"
                        >
                          <span
                            className="material-symbols-outlined text-[16px]"
                            style={{ fontVariationSettings: "'FILL' 1" }}
                          >
                            thumb_down
                          </span>
                          {report.disagrees ?? 0}
                        </span>
                      </span>
                    </div>
                  </div>
                </Link>
              </li>
            )
          })}
        </ul>

        {emptyMessage && (
          <p className="text-center text-lg text-on-surface-variant py-16">{emptyMessage}</p>
        )}

        {isPending && (
          <p className="text-center text-lg text-on-surface-variant py-12">Loading feed…</p>
        )}

        <div ref={loadMoreRef} className="h-6 w-full shrink-0" aria-hidden />

        {isFetchingNextPage && (
          <p className="text-center text-base text-on-surface-variant pb-6">Loading more…</p>
        )}
      </main>

      <FeedLocationPickerModal
        open={pickerOpen}
        onDismiss={() => setPickerOpen(false)}
        center={modalCenter}
        radiusKm={radiusKm}
        tileUrl={tileUrl}
        tileAttr={tileAttr}
        onFeedCenterChange={(c) => setFeedCenterDraft(c)}
        onLocateUnavailable={() =>
          setLocateHint(
            'Could not get your position. Allow location access or pick a point on the map.'
          )
        }
      />
    </div>
  )
}
