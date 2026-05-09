import { useState } from 'react'
import { Link } from 'react-router-dom'
import Navbar from '../components/Navbar.jsx'
import { USER_SEARCH_MIN_LENGTH, useUserSearch } from '../hooks/useUserSearch.js'

function UserCard({ user }) {
  const reports = user.contributionStats?.reportsSubmitted ?? 0
  const routes = user.contributionStats?.routesPlanned ?? 0
  return (
    <Link
      to={`/profile/${user.id}`}
      className="flex items-center gap-3 bg-surface-container-lowest rounded-2xl shadow-sm p-4 hover:shadow-md transition-shadow"
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

function UserSearchPage() {
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
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-4">
        <h1 className="text-2xl font-bold font-headline text-on-surface">Find people</h1>

        <label className="flex flex-col gap-1">
          <span className="text-xs uppercase tracking-wide text-on-surface-variant">
            Search by name or email
          </span>
          <input
            type="search"
            value={query}
            onChange={onChange}
            placeholder="e.g. alice"
            autoFocus
            className="bg-surface-container-lowest rounded-2xl shadow-sm px-4 py-3 text-sm text-on-surface placeholder:text-on-surface-variant outline-none focus:ring-2 focus:ring-primary"
            aria-label="Search users by name or email"
          />
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
                  <UserCard user={u} />
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
      </main>
    </div>
  )
}

export default UserSearchPage
