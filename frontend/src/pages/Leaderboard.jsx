import { Link } from 'react-router-dom'
import Navbar from '../components/Navbar.jsx'
import { useLeaderboard } from '../hooks/useLeaderboard.js'
import { useAuth } from '../context/AuthContext.jsx'

function Avatar({ url, name }) {
  if (url) {
    return (
      <img
        src={url}
        alt=""
        className="w-10 h-10 rounded-full object-cover flex-shrink-0"
      />
    )
  }
  return (
    <div className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center flex-shrink-0">
      <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '22px' }}>
        person
      </span>
    </div>
  )
}

function RankCell({ rank }) {
  // Top 3 get a colored chip; the rest stay neutral. Tie-aware values are
  // already produced by the backend (olympic-style rank assignment), so we
  // never need to display "T-1" — the rank itself does the talking.
  // Top 3 chips use tinted palette colors with `dark:` variants so gold /
  // silver / bronze stay recognizable in both themes without rendering as
  // bright pale blocks on a near-black page.
  const tone =
    rank === 1
      ? 'bg-amber-400/20 text-amber-700 dark:bg-amber-300/15 dark:text-amber-300' // gold
      : rank === 2
        ? 'bg-slate-300/40 text-slate-700 dark:bg-slate-300/15 dark:text-slate-200' // silver
        : rank === 3
          ? 'bg-orange-300/25 text-orange-800 dark:bg-orange-300/15 dark:text-orange-300' // bronze
          : 'bg-surface-container text-on-surface-variant'
  return (
    <div className={`w-12 h-10 rounded-lg flex items-center justify-center font-bold text-sm tabular-nums ${tone}`}>
      #{rank}
    </div>
  )
}

function YourRankBanner({ rank, points }) {
  return (
    <div className="bg-primary/10 border border-primary/30 rounded-2xl p-4 flex items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <span className="material-symbols-outlined text-primary" style={{ fontSize: '24px' }}>
          person_pin
        </span>
        <div className="flex flex-col">
          <span className="text-xs uppercase tracking-wide text-on-surface-variant">Your rank</span>
          <span className="text-2xl font-bold text-on-surface tabular-nums">#{rank}</span>
        </div>
      </div>
      <div className="flex flex-col items-end">
        <span className="text-xs uppercase tracking-wide text-on-surface-variant">Points</span>
        <span className="text-2xl font-bold text-on-surface tabular-nums">{points}</span>
      </div>
    </div>
  )
}

function Leaderboard() {
  const { isAuthenticated } = useAuth()
  const { data, isPending, isError } = useLeaderboard()

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-bold font-headline text-on-surface">Leaderboard</h1>
          <p className="text-sm text-on-surface-variant">
            Top contributors ranked by points. Tied scores share a rank.
          </p>
        </div>

        {isPending && (
          <p className="text-sm text-on-surface-variant">Loading leaderboard…</p>
        )}

        {isError && (
          <p role="alert" className="text-sm text-error">
            Failed to load the leaderboard. Please try again later.
          </p>
        )}

        {!isPending && !isError && data && (
          <>
            {data.callerRank && (
              <YourRankBanner rank={data.callerRank.rank} points={data.callerRank.points} />
            )}

            {!isAuthenticated && (
              <p className="text-xs text-on-surface-variant">
                <Link to="/login" className="text-primary font-semibold hover:underline">
                  Log in
                </Link>{' '}
                to see your own rank on this page.
              </p>
            )}

            {data.entries.length === 0 ? (
              <p className="text-sm text-on-surface-variant">
                No ranked users yet. Submit a report to get on the board.
              </p>
            ) : (
              <ol className="flex flex-col gap-2">
                {data.entries.map((entry) => (
                  <li
                    key={entry.userId}
                    className="bg-surface-container rounded-2xl shadow-sm p-3 flex items-center gap-3"
                  >
                    <RankCell rank={entry.rank} />
                    <Avatar url={entry.avatarUrl} name={entry.name} />
                    <Link
                      to={`/profile/${entry.userId}`}
                      className="flex-1 min-w-0 text-on-surface font-semibold hover:text-primary transition-colors truncate"
                    >
                      {entry.name}
                    </Link>
                    <div className="flex flex-col items-end flex-shrink-0">
                      <span className="text-xs uppercase tracking-wide text-on-surface-variant">Points</span>
                      <span className="font-bold text-on-surface tabular-nums">{entry.points}</span>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </>
        )}
      </main>
    </div>
  )
}

export default Leaderboard
