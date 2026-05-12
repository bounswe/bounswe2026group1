import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
import Navbar from '../components/Navbar.jsx'
import FeedLocationPickerModal from '../components/FeedLocationPickerModal.jsx'
import { useTheme } from '../context/ThemeContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { getReportFeed, mapReport } from '../services/reportService.js'
import { feedFilterChipClass } from '../utils/feedFilterChip.js'
import { STATUS_OPTIONS, SORT_OPTIONS } from '../utils/feedFilterOptions.js'
import { OBJECT_TYPES } from '../utils/objectTypeConfig.js'
import { useUserSearch, USER_SEARCH_MIN_LENGTH } from '../hooks/useUserSearch.js'
import { useReverseGeocode } from '../hooks/useReverseGeocode.js'

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

export { feedFilterChipClass }

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

/** Converts a `<input type="datetime-local">` value to an ISO-8601 instant. Empty → null. */
function localDatetimeToIso(v) {
  if (!v) return null
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

function parseNonNegativeInt(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isInteger(n) && n >= 0 ? n : null
}

function toggleInArray(arr, value) {
  return arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value]
}

const OBJECT_CHIP_BASE =
  'inline-flex items-center gap-2 px-4 py-2 rounded-2xl text-sm font-semibold border transition-colors cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-primary'

function objectTypeChipClass(selected) {
  return selected ? OBJECT_CHIP_BASE : `${OBJECT_CHIP_BASE} bg-surface-container-highest`
}

function objectTypeChipStyle(color, selected) {
  return selected
    ? { backgroundColor: color, borderColor: color, color: '#ffffff' }
    : { borderColor: 'transparent', borderLeft: `4px solid ${color}` }
}

/**
 * Issue groups for the advanced filter, keyed by object type. Order follows the user's click
 * order in `selectedObjectTypes` so issue groups appear in the same order the user picked
 * their object types — visually matching the colored chips above.
 */
function buildIssueGroups(selectedObjectTypes) {
  if (selectedObjectTypes.length === 0) return []
  return selectedObjectTypes
    .map((type) => OBJECT_TYPES.find((t) => t.type === type))
    .filter(Boolean)
    .map((t) => ({
      type: t.type,
      label: t.label,
      color: t.markerColor,
      issues: t.issues,
    }))
}

/** All issue keys valid for the given object types (used to prune orphan selections). */
function validIssueKeysFor(selectedObjectTypes) {
  const keys = new Set()
  for (const t of OBJECT_TYPES) {
    if (selectedObjectTypes.includes(t.type)) {
      for (const i of t.issues) keys.add(i.key)
    }
  }
  return keys
}

function ReportFeedCard({ report, distLabel, env, rtype }) {
  // Backend stores a reverse-geocoded label at create time, but older rows
  // (and external imports) ship without one. Resolve it client-side so the
  // card never falls back to raw "lat, lng" digits.
  const { data: geocodedLabel, isLoading: geocoding } = useReverseGeocode(
    report.latitude,
    report.longitude,
    { enabled: !report.locationLabel }
  )

  const locationText = report.locationLabel
    || geocodedLabel
    || (geocoding ? 'Resolving location…' : 'Location unavailable')

  return (
    <li key={report.id} className="min-w-0">
      <Link
        to={`/?report=${report.id}&from=feed`}
        className="flex flex-col h-full rounded-xl border border-outline-variant/20 bg-surface-container-low shadow-sm p-3 hover:shadow-md transition-shadow group"
      >
        <div className="relative aspect-[5/3] max-h-[140px] w-full rounded-lg overflow-hidden bg-surface-container-high mb-2">
          {report.image ? (
            <img
              src={report.image}
              alt=""
              className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-on-surface-variant">
              <span className="material-symbols-outlined text-4xl opacity-40">image</span>
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

        <div className="flex flex-wrap gap-1 mb-1.5">
          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
              STATUS_STYLES[report.status] ?? 'bg-surface-container-high text-on-surface'
            }`}
          >
            {STATUS_LABELS[report.status] ?? report.status}
          </span>
          {rtype && (
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full border border-primary/25 bg-primary/8 text-primary dark:bg-primary/15 dark:text-primary">
              {rtype}
            </span>
          )}
          {env && (
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full border border-outline-variant/35 bg-surface-container-high text-on-surface-variant">
              {env}
            </span>
          )}
        </div>

        <h2 className="text-sm md:text-base font-bold font-headline text-on-surface leading-snug line-clamp-2 mb-1">
          {report.title}
        </h2>

        {report.description ? (
          <p className="text-xs text-on-surface-variant line-clamp-2 mb-2 flex-1">
            {report.description}
          </p>
        ) : (
          <p className="text-xs text-on-surface-variant/70 italic mb-2 flex-1">
            No description
          </p>
        )}

        <div className="mt-auto pt-1.5 border-t border-outline-variant/20 flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[10px] text-on-surface-variant">
            <span className="inline-flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px] text-on-surface-variant">
                calendar_today
              </span>
              {report.date}
            </span>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-1.5 text-[10px]">
            <span className="inline-flex items-center gap-1 text-on-surface-variant truncate max-w-[55%] min-w-0">
              <span
                className="material-symbols-outlined text-[14px] text-primary shrink-0"
                style={{ fontVariationSettings: "'FILL' 1" }}
                aria-hidden
              >
                location_on
              </span>
              <span className="truncate font-medium text-on-surface text-[11px]">{locationText}</span>
            </span>
            <span className="inline-flex items-center gap-2 shrink-0 text-xs font-semibold not-italic">
              <span
                className="inline-flex items-center gap-1 text-primary"
                title="Agree"
              >
                <span
                  className="material-symbols-outlined text-[14px]"
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
                  className="material-symbols-outlined text-[14px]"
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
}

export default function Feed() {
  const { resolved: themeResolved } = useTheme()
  const isDark = themeResolved === 'dark'
  const tileUrl = isDark ? TILE_DARK : TILE_LIGHT
  const tileAttr = isDark ? TILE_ATTR_DARK : TILE_ATTR_LIGHT
  const { token, isAuthenticated } = useAuth()

  const [pickerOpen, setPickerOpen] = useState(false)
  const [feedCenterDraft, setFeedCenterDraft] = useState(null)
  const [radiusKmInput, setRadiusKmInput] = useState('5')
  const radiusKm = clampRadiusKm(radiusKmInput)

  const [reportTypeFilter, setReportTypeFilter] = useState('ALL')
  const [environmentFilter, setEnvironmentFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState(/** @type {string[]} */ ([]))
  const [sortFilter, setSortFilter] = useState('NEWEST')

  // Free-text search: draft updates immediately, applied is debounced (300ms) to avoid hammering the API.
  const [qDraft, setQDraft] = useState('')
  const [qApplied, setQApplied] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setQApplied(qDraft.trim()), 300)
    return () => clearTimeout(t)
  }, [qDraft])

  // Advanced filters live behind a toggle to keep the default bar uncluttered.
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [objectTypeFilter, setObjectTypeFilter] = useState(/** @type {string[]} */ ([]))
  const [issueTypeFilter, setIssueTypeFilter] = useState(/** @type {string[]} */ ([]))
  // Drop orphan issues whenever the user narrows or clears their object selection — an
  // unrelated issue chip silently filtering the feed is confusing.
  useEffect(() => {
    if (objectTypeFilter.length === 0) {
      setIssueTypeFilter((prev) => (prev.length === 0 ? prev : []))
      return
    }
    const valid = validIssueKeysFor(objectTypeFilter)
    setIssueTypeFilter((prev) => {
      const filtered = prev.filter((k) => valid.has(k))
      return filtered.length === prev.length ? prev : filtered
    })
  }, [objectTypeFilter])
  /** @type {[null | {id:number,name:string}, Function]} */
  const [selectedAuthor, setSelectedAuthor] = useState(null)
  const [authorQuery, setAuthorQuery] = useState('')
  const [publishedAfterInput, setPublishedAfterInput] = useState('')
  const [publishedBeforeInput, setPublishedBeforeInput] = useState('')
  const [minAgreesInput, setMinAgreesInput] = useState('')
  const [minDisagreesInput, setMinDisagreesInput] = useState('')

  const [locateHint, setLocateHint] = useState(null)

  useEffect(() => {
    if (!locateHint) return
    const t = setTimeout(() => setLocateHint(null), 4000)
    return () => clearTimeout(t)
  }, [locateHint])

  const feedLat =
    feedCenterDraft != null && Number.isFinite(Number(feedCenterDraft.lat))
      ? Number(feedCenterDraft.lat)
      : null
  const feedLng =
    feedCenterDraft != null && Number.isFinite(Number(feedCenterDraft.lng))
      ? Number(feedCenterDraft.lng)
      : null

  // Resolved author id comes from autocomplete selection — typing a name without picking from
  // the dropdown does not filter the feed (avoids ambiguous matches across duplicate names).
  const authorIdNum = selectedAuthor?.id ?? null

  const publishedAfterIso = useMemo(
    () => localDatetimeToIso(publishedAfterInput),
    [publishedAfterInput]
  )
  const publishedBeforeIso = useMemo(
    () => localDatetimeToIso(publishedBeforeInput),
    [publishedBeforeInput]
  )

  const minAgreesNum = parseNonNegativeInt(minAgreesInput)
  const minDisagreesNum = parseNonNegativeInt(minDisagreesInput)

  // Serialize set-like filters into stable strings so the queryKey is referentially stable.
  const statusKey = statusFilter.join(',')
  const objectTypeKey = objectTypeFilter.join(',')
  const issueTypeKey = issueTypeFilter.join(',')

  const feedQueryKey = useMemo(
    () => [
      'reportFeed',
      reportTypeFilter,
      environmentFilter,
      statusKey,
      sortFilter,
      qApplied,
      authorIdNum,
      publishedAfterIso,
      publishedBeforeIso,
      minAgreesNum,
      minDisagreesNum,
      objectTypeKey,
      issueTypeKey,
      feedLat,
      feedLng,
      radiusKm,
      token ?? '',
    ],
    [
      reportTypeFilter,
      environmentFilter,
      statusKey,
      sortFilter,
      qApplied,
      authorIdNum,
      publishedAfterIso,
      publishedBeforeIso,
      minAgreesNum,
      minDisagreesNum,
      objectTypeKey,
      issueTypeKey,
      feedLat,
      feedLng,
      radiusKm,
      token,
    ]
  )

  // Block the request entirely when the selected sort needs data we don't have — surfacing
  // a clear UI hint is much better than a 400 round-trip + generic "couldn't load" error.
  const feedBlockedReason =
    sortFilter === 'DISTANCE' && (feedLat == null || feedLng == null)
      ? 'Pick a reference point to sort by distance.'
      : null

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isPending,
    isFetching,
    isPlaceholderData,
    isError,
    error,
  } = useInfiniteQuery({
    queryKey: feedQueryKey,
    enabled: feedBlockedReason == null,
    // Keeps the previous page list visible while a filter change refetches — without it the
    // list flashes empty, the page collapses, and the scroll position jumps to the top.
    placeholderData: keepPreviousData,
    queryFn: ({ pageParam, signal }) => {
      const hasLocation = feedLat != null && feedLng != null
      return getReportFeed(
        {
          page: pageParam,
          size: 20,
          reportType: reportTypeFilter,
          environment: environmentFilter,
          status: statusFilter.length ? statusFilter : undefined,
          authorId: authorIdNum ?? undefined,
          publishedAfter: publishedAfterIso ?? undefined,
          publishedBefore: publishedBeforeIso ?? undefined,
          q: qApplied || undefined,
          minAgrees: minAgreesNum ?? undefined,
          minDisagrees: minDisagreesNum ?? undefined,
          objectType: objectTypeFilter.length ? objectTypeFilter : undefined,
          issueType: issueTypeFilter.length ? issueTypeFilter : undefined,
          sort: sortFilter,
          latitude: hasLocation ? feedLat : undefined,
          longitude: hasLocation ? feedLng : undefined,
          radiusInKm: hasLocation ? radiusKm : undefined,
        },
        token,
        { signal }
      )
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
  })

  const reports = useMemo(() => {
    if (!data?.pages) return []
    return data.pages.flatMap((p) => (p.content || []).map(mapReport))
  }, [data])

  /** Ensures chips match visible cards if the API ever returns extra rows (e.g. proximity + enum filters). */
  const reportsMatchingFilters = useMemo(() => {
    return reports.filter((r) => {
      if (reportTypeFilter !== 'ALL' && r.reportType !== reportTypeFilter) return false
      if (environmentFilter !== 'ALL' && r.environment !== environmentFilter) return false
      return true
    })
  }, [reports, reportTypeFilter, environmentFilter])

  const modalCenter = feedCenterDraft ?? DEFAULT_CENTER
  const referenceForDistance = feedCenterDraft

  // Client-side distance re-sort is a safety net for the DISTANCE case where the backend
  // already orders by ST_Distance. Any other explicit sort (NEWEST, MOST_AGREED, …) must
  // be left in the order the backend returned it.
  const reportsSorted = useMemo(() => {
    const ref = referenceForDistance
    const useDistance = ref && sortFilter === 'DISTANCE'
    if (!useDistance) return reportsMatchingFilters
    return [...reportsMatchingFilters].sort((a, b) => {
      const da = haversineKm(ref.lat, ref.lng, Number(a.latitude), Number(a.longitude))
      const db = haversineKm(ref.lat, ref.lng, Number(b.latitude), Number(b.longitude))
      const fa = Number.isFinite(da) ? da : Number.POSITIVE_INFINITY
      const fb = Number.isFinite(db) ? db : Number.POSITIVE_INFINITY
      return fa - fb
    })
  }, [reportsMatchingFilters, referenceForDistance, sortFilter])

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
    reportTypeFilter !== 'ALL' ||
    environmentFilter !== 'ALL' ||
    statusFilter.length > 0 ||
    qApplied.length > 0 ||
    authorIdNum != null ||
    publishedAfterIso != null ||
    publishedBeforeIso != null ||
    minAgreesNum != null ||
    minDisagreesNum != null ||
    objectTypeFilter.length > 0 ||
    issueTypeFilter.length > 0

  function resetAdvancedFilters() {
    setStatusFilter([])
    setSortFilter('NEWEST')
    setQDraft('')
    setQApplied('')
    setObjectTypeFilter([])
    setIssueTypeFilter([])
    setSelectedAuthor(null)
    setAuthorQuery('')
    setPublishedAfterInput('')
    setPublishedBeforeInput('')
    setMinAgreesInput('')
    setMinDisagreesInput('')
  }

  // Suppress the empty / loading message while a filter-change refetch is in flight and we're
  // still showing placeholder (i.e. previous) data — otherwise it would briefly overlay the list.
  const isRefreshingWithPlaceholder = isFetching && isPlaceholderData
  const emptyMessage = useMemo(() => {
    if (feedBlockedReason) return null
    if (reportsSorted.length > 0 || isPending || isError) return null
    if (isRefreshingWithPlaceholder) return null
    if (hasActiveFilter) return 'No reports match your filters.'
    if (feedCenterDraft != null) return 'No reports within this search radius.'
    return 'No reports yet.'
  }, [
    feedBlockedReason,
    reportsSorted.length,
    isPending,
    isError,
    isRefreshingWithPlaceholder,
    hasActiveFilter,
    feedCenterDraft,
  ])

  // Hide the dropdown once a user is selected so the previous result list doesn't linger.
  const authorSearchActive = !selectedAuthor && authorQuery.trim().length >= USER_SEARCH_MIN_LENGTH
  const { data: authorSearchPage, isFetching: authorSearchFetching } = useUserSearch(
    authorSearchActive ? authorQuery : '',
    { page: 0, size: 8 }
  )
  const authorOptions = authorSearchActive ? authorSearchPage?.content ?? [] : []

  function clearSelectedAuthor() {
    setSelectedAuthor(null)
    setAuthorQuery('')
  }

  function pickAuthor(option) {
    setSelectedAuthor({ id: option.id, name: option.name })
    setAuthorQuery(option.name)
  }

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

        <section className="rounded-2xl border border-outline-variant/20 bg-surface-container-low shadow-sm p-6 md:p-8 flex flex-col gap-6">
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

          {/* Status (multi) + sort + search row */}
          <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
            <div className="flex flex-col gap-2 min-w-0">
              <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                Status
              </span>
              <div className="flex flex-wrap gap-2">
                {STATUS_OPTIONS.map(({ id, label }) => {
                  const selected = statusFilter.includes(id)
                  return (
                    <button
                      key={id}
                      type="button"
                      aria-pressed={selected}
                      aria-label={`Status: ${label}`}
                      className={feedFilterChipClass(selected)}
                      onClick={() => setStatusFilter((prev) => toggleInArray(prev, id))}
                    >
                      {label}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="flex flex-col gap-2 min-w-0">
              <label
                htmlFor="feed-sort"
                className="text-xs font-bold uppercase tracking-wide text-on-surface-variant"
              >
                Sort
              </label>
              <select
                id="feed-sort"
                value={sortFilter}
                onChange={(e) => setSortFilter(e.target.value)}
                className="rounded-2xl border border-outline-variant bg-surface-container-highest px-3 py-2 text-sm font-semibold text-on-surface focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                {SORT_OPTIONS.map(({ id, label }) => (
                  <option key={id} value={id}>
                    {label}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-2 flex-1 min-w-[14rem]">
              <label
                htmlFor="feed-q"
                className="text-xs font-bold uppercase tracking-wide text-on-surface-variant"
              >
                Search description
              </label>
              <input
                id="feed-q"
                type="search"
                inputMode="search"
                placeholder="e.g. elevator, ramp, sidewalk"
                value={qDraft}
                onChange={(e) => setQDraft(e.target.value)}
                className="rounded-2xl border border-outline-variant bg-background px-4 py-2 text-sm text-on-surface focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => setPickerOpen(true)}
              className={`${feedFilterChipClass(false)} gap-2`}
            >
              <span className="material-symbols-outlined text-lg" aria-hidden>
                map
              </span>
              Choose on map
            </button>
            <button
              type="button"
              onClick={handleUseCurrentLocation}
              className={`${feedFilterChipClass(false)} gap-2`}
            >
              <span className="material-symbols-outlined text-lg" aria-hidden>
                my_location
              </span>
              Use current location
            </button>
            <button
              type="button"
              onClick={() => setAdvancedOpen((v) => !v)}
              aria-expanded={advancedOpen}
              aria-controls="feed-advanced-filters"
              className={`${feedFilterChipClass(advancedOpen)} gap-2`}
            >
              <span className="material-symbols-outlined text-lg" aria-hidden>
                tune
              </span>
              Advanced filters
            </button>
            {hasActiveFilter && (
              <button
                type="button"
                onClick={resetAdvancedFilters}
                className="text-sm font-semibold text-primary hover:bg-primary/10 px-3 py-2 rounded-2xl cursor-pointer"
              >
                Clear all
              </button>
            )}
          </div>

          {feedCenterDraft != null && (
            <p className="text-sm text-on-surface-variant">
              Reference point: {feedCenterDraft.lat.toFixed(4)}, {feedCenterDraft.lng.toFixed(4)}
            </p>
          )}

          {advancedOpen && (
            <div
              id="feed-advanced-filters"
              className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-outline-variant/20"
            >
              <div className="flex flex-col gap-2">
                <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  Object type
                </span>
                <div className="flex flex-wrap gap-2">
                  {OBJECT_TYPES.map((t) => {
                    const selected = objectTypeFilter.includes(t.type)
                    return (
                      <button
                        key={t.type}
                        type="button"
                        aria-pressed={selected}
                        aria-label={`Object type: ${t.label}`}
                        onClick={() => setObjectTypeFilter((prev) => toggleInArray(prev, t.type))}
                        className={objectTypeChipClass(selected)}
                        style={objectTypeChipStyle(t.markerColor, selected)}
                      >
                        <span
                          aria-hidden
                          className="inline-block w-2 h-2 rounded-full shrink-0"
                          style={{ backgroundColor: selected ? '#ffffff' : t.markerColor }}
                        />
                        <span>{t.label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                  Issue type
                </span>
                {objectTypeFilter.length === 0 ? (
                  <p className="text-xs text-on-surface-variant italic">
                    Pick at least one object type to choose issue types.
                  </p>
                ) : (
                  <div className="flex flex-col gap-3 max-h-60 overflow-y-auto pr-1">
                    {buildIssueGroups(objectTypeFilter).map((group) => (
                      <div key={group.type} className="flex flex-col gap-1.5">
                        <div className="flex items-center gap-2">
                          <span
                            aria-hidden
                            className="inline-block w-2.5 h-2.5 rounded-full shrink-0"
                            style={{ backgroundColor: group.color }}
                          />
                          <span className="text-[11px] font-bold uppercase tracking-wide text-on-surface">
                            {group.label}
                          </span>
                        </div>
                        <div className="flex flex-wrap gap-1.5 pl-4">
                          {group.issues.map((i) => {
                            const selected = issueTypeFilter.includes(i.key)
                            return (
                              <button
                                key={`${group.type}:${i.key}`}
                                type="button"
                                aria-pressed={selected}
                                aria-label={`${group.label} issue: ${i.label}`}
                                onClick={() =>
                                  setIssueTypeFilter((prev) => toggleInArray(prev, i.key))
                                }
                                className={`${feedFilterChipClass(selected)} text-xs px-3 py-1.5`}
                                style={
                                  selected
                                    ? undefined
                                    : { borderLeft: `4px solid ${group.color}` }
                                }
                              >
                                {i.label}
                              </button>
                            )
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex flex-col gap-1 relative">
                <label
                  htmlFor="feed-author-name"
                  className="text-xs font-bold uppercase tracking-wide text-on-surface-variant"
                >
                  Author
                </label>
                {selectedAuthor ? (
                  <div className="flex items-center gap-2 rounded-xl border border-primary/30 bg-primary/10 px-3 py-2 text-sm text-on-surface">
                    <span className="material-symbols-outlined text-base text-primary" aria-hidden>
                      person
                    </span>
                    <span className="truncate font-medium">{selectedAuthor.name}</span>
                    <button
                      type="button"
                      onClick={clearSelectedAuthor}
                      aria-label="Clear author filter"
                      className="ml-auto w-6 h-6 rounded-full hover:bg-primary/20 flex items-center justify-center cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-base">close</span>
                    </button>
                  </div>
                ) : (
                  <>
                    <input
                      id="feed-author-name"
                      type="search"
                      autoComplete="off"
                      placeholder={
                        isAuthenticated
                          ? 'Type at least 2 letters of a name'
                          : 'Sign in to filter by author'
                      }
                      value={authorQuery}
                      disabled={!isAuthenticated}
                      onChange={(e) => setAuthorQuery(e.target.value)}
                      className="rounded-xl border border-outline-variant bg-background px-3 py-2 text-sm text-on-surface disabled:bg-surface-container disabled:cursor-not-allowed"
                    />
                    {authorSearchActive && (
                      <div
                        role="listbox"
                        aria-label="Author search results"
                        className="absolute left-0 right-0 top-full mt-1 z-20 max-h-56 overflow-y-auto rounded-xl border border-outline-variant bg-surface-container-lowest shadow-md"
                      >
                        {authorSearchFetching && authorOptions.length === 0 && (
                          <p className="px-3 py-2 text-xs text-on-surface-variant">Searching…</p>
                        )}
                        {!authorSearchFetching && authorOptions.length === 0 && (
                          <p className="px-3 py-2 text-xs text-on-surface-variant">No users found.</p>
                        )}
                        {authorOptions.map((u) => (
                          <button
                            key={u.id}
                            type="button"
                            role="option"
                            aria-selected={false}
                            onClick={() => pickAuthor(u)}
                            className="block w-full text-left px-3 py-2 text-sm text-on-surface hover:bg-primary/10 cursor-pointer"
                          >
                            {u.name}
                          </button>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                    Published after
                  </span>
                  <input
                    type="datetime-local"
                    value={publishedAfterInput}
                    onChange={(e) => setPublishedAfterInput(e.target.value)}
                    className="rounded-xl border border-outline-variant bg-background px-3 py-2 text-sm text-on-surface"
                  />
                </label>
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                    Published before
                  </span>
                  <input
                    type="datetime-local"
                    value={publishedBeforeInput}
                    onChange={(e) => setPublishedBeforeInput(e.target.value)}
                    className="rounded-xl border border-outline-variant bg-background px-3 py-2 text-sm text-on-surface"
                  />
                </label>
              </div>

              <div className="grid grid-cols-2 gap-3 md:col-span-2">
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                    Min agrees
                  </span>
                  <input
                    type="number"
                    min={0}
                    step={1}
                    value={minAgreesInput}
                    onChange={(e) => setMinAgreesInput(e.target.value)}
                    className="rounded-xl border border-outline-variant bg-background px-3 py-2 text-sm text-on-surface"
                  />
                </label>
                <label className="flex flex-col gap-1">
                  <span className="text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                    Min disagrees
                  </span>
                  <input
                    type="number"
                    min={0}
                    step={1}
                    value={minDisagreesInput}
                    onChange={(e) => setMinDisagreesInput(e.target.value)}
                    className="rounded-xl border border-outline-variant bg-background px-3 py-2 text-sm text-on-surface"
                  />
                </label>
              </div>
            </div>
          )}
        </section>

        {locateHint && (
          <p role="status" className="text-sm text-on-surface-variant">
            {locateHint}
          </p>
        )}

        {feedBlockedReason && (
          <div
            role="status"
            className="flex flex-wrap items-center gap-3 rounded-2xl border border-amber-300 bg-amber-50 dark:border-amber-700/50 dark:bg-amber-950/40 px-4 py-3 text-sm text-amber-900 dark:text-amber-100"
          >
            <span className="material-symbols-outlined text-base shrink-0" aria-hidden>
              location_searching
            </span>
            <span className="flex-1">{feedBlockedReason}</span>
            <button
              type="button"
              onClick={handleUseCurrentLocation}
              className="text-sm font-semibold underline underline-offset-2 hover:text-amber-700 dark:hover:text-amber-50"
            >
              Use current location
            </button>
            <button
              type="button"
              onClick={() => setPickerOpen(true)}
              className="text-sm font-semibold underline underline-offset-2 hover:text-amber-700 dark:hover:text-amber-50"
            >
              Choose on map
            </button>
          </div>
        )}

        {isError && !feedBlockedReason && (
          <p role="alert" className="text-sm text-error font-medium">
            {error?.message || 'Could not load the feed.'}
          </p>
        )}

        <ul
          aria-busy={isRefreshingWithPlaceholder ? 'true' : 'false'}
          hidden={feedBlockedReason != null}
          className={`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-4 list-none p-0 m-0 transition-opacity duration-150 ${isRefreshingWithPlaceholder ? 'opacity-60' : 'opacity-100'}`}
        >
          {reportsSorted.map((report) => {
            const distLabel = formatDistanceKm(
              referenceForDistance,
              report.latitude,
              report.longitude
            )
            return (
              <ReportFeedCard
                key={report.id}
                report={report}
                distLabel={distLabel}
                env={envLabel(report.environment)}
                rtype={typeLabel(report.reportType)}
              />
            )
          })}
        </ul>

        {emptyMessage && (
          <p className="text-center text-lg text-on-surface-variant py-16">{emptyMessage}</p>
        )}

        {isPending && !feedBlockedReason && (
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
