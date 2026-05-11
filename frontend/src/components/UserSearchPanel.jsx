import { useState } from 'react'
import { Link } from 'react-router-dom'
import { USER_SEARCH_MIN_LENGTH, useUserSearch } from '../hooks/useUserSearch.js'

/**
 * Self-contained user search panel — input + paginated results.
 *
 * Used both as the body of /search/users (full page, deep-linkable) and as
 * the contents of the navbar UserSearchDropdown (popover on desktop so the
 * map stays visible). Behaviour is identical in both mounts; layout is
 * driven by the parent via the className-overridable wrapper.
 *
 * Props:
 *  - onResultSelect?: () => void   Fired after a result row is clicked (e.g.
 *                                  the dropdown closes itself).
 *  - autoFocus?:      boolean      Default true.
 *  - heading?:        string       Optional heading rendered above the input.
 */
function UserCard({ user, onSelect }) {
  const reports = user.contributionStats?.reportsSubmitted ?? 0
  const routes = user.contributionStats?.routesPlanned ?? 0
  return (
    <Link
      to={`/profile/${user.id}`}
      onClick={onSelect}
      className="flex items-center gap-3 bg-surface-container rounded-2xl shadow-sm p-4 hover:shadow-md transition-shadow"
    >
      <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center flex-shrink-0 overflow-hidden">
        {user.avatarUrl ? (
          <img src={user.avatarUrl} alt={user.name} className="w-full h-full object-cover" />
        ) : (
          <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '24px' }}>
            person
          </span>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-on-surface truncate">{user.name}</p>
        <p className="text-xs text-on-surface-variant">
          {reports} report{reports === 1 ? '' : 's'} · {routes} route{routes === 1 ? '' : 's'}
        </p>
      </div>
      <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-primary/10 text-primary">
        {user.role}
      </span>
    </Link>
  )
}

function UserSearchPanel({ onResultSelect, autoFocus = true, heading }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const trimmed = query.trim()
  const tooShort = trimmed.length > 0 && trimmed.length < USER_SEARCH_MIN_LENGTH

  const { data, isPending, isFetching, isError, error } = useUserSearch(query, { page })

  const results = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0
  const isFirstPage = page === 0
  const isLastPage = data ? data.last : true

  function onChange(e) {
    setQuery(e.target.value)
    setPage(0)
  }

  return (
    <>
      {heading && (
        <h1 className="text-2xl font-bold font-headline text-on-surface">{heading}</h1>
      )}

      <label className="flex flex-col gap-2 -mt-2">
        <span className="text-xs text-on-surface-variant">
          Search by name or email
        </span>
        <div className="relative">
          <input
            type="text"
            value={query}
            onChange={onChange}
            placeholder="e.g. alice"
            autoFocus={autoFocus}
            className="w-full bg-surface-container-high rounded-2xl shadow-sm pl-4 pr-10 py-3 text-sm text-on-surface placeholder:text-on-surface-variant outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label="Search users by name or email"
          />
          {query.length > 0 && (
            <button
              type="button"
              onClick={() => { setQuery(''); setPage(0) }}
              aria-label="Clear search"
              className="absolute right-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors cursor-pointer"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          )}
        </div>
      </label>

      {tooShort && (
        <p className="text-sm text-on-surface-variant">
          Type at least {USER_SEARCH_MIN_LENGTH} characters to search.
        </p>
      )}

      {trimmed.length >= USER_SEARCH_MIN_LENGTH && isPending && (
        <p className="text-sm text-on-surface-variant">Searching…</p>
      )}

      {isError && (
        <p role="alert" className="text-sm text-error">
          {error?.message || 'Search failed.'}
        </p>
      )}

      {!isPending && !isError && data && (
        <>
          <p className="text-xs text-on-surface-variant" aria-live="polite">
            {totalElements === 0
              ? 'No users found.'
              : `${totalElements} match${totalElements === 1 ? '' : 'es'}.`}
          </p>

          <ul className="flex flex-col gap-2">
            {results.map((u) => (
              <li key={u.id}>
                <UserCard user={u} onSelect={onResultSelect} />
              </li>
            ))}
          </ul>

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 mt-2">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                disabled={isFirstPage || isFetching}
                className="px-3 py-1.5 rounded-full bg-surface-container-low text-on-surface text-sm font-semibold disabled:opacity-50 cursor-pointer"
              >
                Previous
              </button>
              <span className="text-xs text-on-surface-variant">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={isLastPage || isFetching}
                className="px-3 py-1.5 rounded-full bg-primary text-on-primary text-sm font-semibold disabled:opacity-50 cursor-pointer"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </>
  )
}

export default UserSearchPanel
