import { useRef, useState } from 'react'

/**
 * Parse "lat, lng" or two decimal numbers (same helpers as feed map modal).
 */
function tryParseLatLngInput(raw) {
  const s = raw.trim()
  if (!s) return null
  const commaPair = s.match(/^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$/)
  if (commaPair) {
    const lat = Number(commaPair[1])
    const lng = Number(commaPair[2])
    if (Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
      return { lat, lng }
    }
    return null
  }
  const parts = s.split(/[\s,]+/).filter(Boolean)
  if (parts.length >= 2) {
    const lat = Number(parts[0])
    const lng = Number(parts[1])
    if (Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
      return { lat, lng }
    }
  }
  return null
}

/**
 * Floating location search — same UI and Nominatim behavior as the Home map.
 *
 * @param {object} props
 * @param {{ lat: number, lng: number } | null} props.mapCenter — biases search (viewbox + sort)
 * @param {(loc: { lat: number, lon: number }) => void} props.onLocationPicked — `lon` matches Home / Leaflet convention
 * @param {string} [props.initialValue]
 * @param {string} [props.inputId]
 * @param {React.ReactNode} [props.filterSlot] — optional content rendered on the right of the search input
 *   (used by Home for the category-filter button + dropdown). The slot is wrapped in a `relative` flex
 *   container so a popover can position itself against it.
 */
export default function MapSearchBar({
  mapCenter,
  onLocationPicked,
  initialValue = 'Boğaziçi, Istanbul',
  inputId = 'map-search-location',
  filterSlot = null,
}) {
  const [searchValue, setSearchValue] = useState(initialValue)
  const [searchError, setSearchError] = useState('')
  const [searchSuggestions, setSearchSuggestions] = useState([])
  const searchDebounce = useRef(null)

  function handleSearchChange(e) {
    const query = e.target.value
    setSearchValue(query)
    setSearchError('')
    clearTimeout(searchDebounce.current)
    if (query.trim().length < 2) {
      setSearchSuggestions([])
      return
    }
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
    onLocationPicked({
      lat: parseFloat(suggestion.lat),
      lon: parseFloat(suggestion.lon),
    })
  }

  async function handleSearchKeyDown(e) {
    if (e.key === 'Escape') {
      setSearchSuggestions([])
      e.target.blur()
      return
    }
    if (e.key !== 'Enter') return
    const query = searchValue.trim()
    if (!query) return
    setSearchError('')
    setSearchSuggestions([])
    e.target.blur()

    const parsed = tryParseLatLngInput(query)
    if (parsed) {
      onLocationPicked({ lat: parsed.lat, lon: parsed.lng })
      return
    }

    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`
      )
      const results = await res.json()
      if (!results.length) {
        setSearchError('No results found.')
        return
      }
      onLocationPicked({
        lat: parseFloat(results[0].lat),
        lon: parseFloat(results[0].lon),
      })
    } catch {
      setSearchError('Search failed. Please try again.')
    }
  }

  return (
    <div className="absolute top-3 sm:top-6 left-2 right-2 sm:left-1/2 sm:right-auto sm:-translate-x-1/2 sm:w-full sm:max-w-2xl sm:px-6 z-[1000] pointer-events-none">
      <div className="flex items-center bg-surface-container-lowest/80 dark:bg-surface-container-lowest/95 backdrop-blur-md rounded-2xl px-3 sm:px-6 py-2 sm:py-3 gap-2 sm:gap-4 shadow-[0_10px_40px_-4px_rgba(45,47,47,0.12)] border border-outline-variant/20 pointer-events-auto">
        <span className="material-symbols-outlined text-primary text-xl sm:text-2xl">location_on</span>
        <div className="flex-1 min-w-0">
          <p className="hidden sm:block text-[10px] uppercase tracking-wider font-bold text-secondary">Current Location</p>
          <input
            id={inputId}
            type="text"
            value={searchValue}
            onChange={handleSearchChange}
            onKeyDown={handleSearchKeyDown}
            onFocus={() => setSearchValue('')}
            onBlur={() => setTimeout(() => setSearchSuggestions([]), 150)}
            placeholder="Search location..."
            className="bg-transparent border-none p-0 w-full text-on-surface font-headline font-semibold focus:ring-0 text-sm outline-none"
          />
        </div>
        {filterSlot && (
          <>
            <div className="hidden sm:block h-8 w-px bg-outline-variant/30" />
            <div className="relative flex-shrink-0">{filterSlot}</div>
          </>
        )}
      </div>
      {searchSuggestions.length > 0 && (
        <ul className="mt-1 bg-surface-container-lowest rounded-2xl shadow-lg border border-outline-variant/10 overflow-hidden pointer-events-auto">
          {searchSuggestions.map((s) => (
            <li key={s.place_id}>
              <button
                type="button"
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
        <p className="mt-2 text-xs text-error bg-surface-container-lowest/90 rounded-xl px-4 py-2 shadow">{searchError}</p>
      )}
    </div>
  )
}
