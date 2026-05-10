import Navbar from '../components/Navbar.jsx'
import ReportCard from '../components/ReportCard.jsx'
import { useReports } from '../hooks/useReports.js'

function Skeleton() {
  return (
    <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-4 flex flex-col gap-3 animate-pulse">
      <div className="flex items-center gap-2">
        <div className="w-8 h-8 rounded-full bg-surface-container" />
        <div className="h-3 w-32 bg-surface-container rounded" />
      </div>
      <div className="w-full h-44 bg-surface-container rounded-xl" />
      <div className="h-4 w-3/4 bg-surface-container rounded" />
      <div className="h-3 w-full bg-surface-container rounded" />
      <div className="h-3 w-2/3 bg-surface-container rounded" />
    </div>
  )
}

function Feed() {
  const { data, isPending, isError, error } = useReports()

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background font-body">
      <Navbar />
      <main className="flex-1 overflow-auto">
        <div className="max-w-5xl mx-auto p-4 md:p-8 flex flex-col gap-4">
          <header>
            <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">
              Community feed
            </h1>
            <p className="text-sm text-on-surface-variant mt-1">
              Recent accessibility reports from the neighbourhood.
            </p>
          </header>

          {isPending && (
            <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[0, 1, 2, 3].map((i) => (
                <li key={i}><Skeleton /></li>
              ))}
            </ul>
          )}

          {isError && (
            <p role="alert" className="text-sm text-error">
              {error?.message || 'Failed to load the feed.'}
            </p>
          )}

          {!isPending && !isError && data && data.length === 0 && (
            <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-8 text-center">
              <span className="material-symbols-outlined text-4xl text-outline-variant">
                forum
              </span>
              <p className="mt-2 text-sm text-on-surface-variant">
                No reports yet. Be the first — drop a pin on the map to file one.
              </p>
            </div>
          )}

          {!isPending && !isError && data && data.length > 0 && (
            <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {data.map((r) => (
                <li key={r.id}>
                  <ReportCard report={r} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </div>
  )
}

export default Feed
