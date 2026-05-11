import { Link } from 'react-router-dom'
import { Trans, useTranslation } from 'react-i18next'
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
  const tone =
    rank === 1
      ? 'bg-[#fde68a] text-[#92400e]' // gold
      : rank === 2
        ? 'bg-[#e5e7eb] text-[#374151]' // silver
        : rank === 3
          ? 'bg-[#fed7aa] text-[#9a3412]' // bronze
          : 'bg-surface-container text-on-surface-variant'
  return (
    <div className={`w-12 h-10 rounded-lg flex items-center justify-center font-bold text-sm tabular-nums ${tone}`}>
      #{rank}
    </div>
  )
}

function YourRankBanner({ rank, points }) {
  const { t } = useTranslation()
  return (
    <div className="bg-primary/5 border border-primary/20 rounded-2xl p-4 flex items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <span className="material-symbols-outlined text-primary" style={{ fontSize: '24px' }}>
          person_pin
        </span>
        <div className="flex flex-col">
          <span className="text-xs uppercase tracking-wide text-on-surface-variant">{t('leaderboard.yourRank')}</span>
          <span className="text-2xl font-bold text-on-surface tabular-nums">#{rank}</span>
        </div>
      </div>
      <div className="flex flex-col items-end">
        <span className="text-xs uppercase tracking-wide text-on-surface-variant">{t('leaderboard.points')}</span>
        <span className="text-2xl font-bold text-on-surface tabular-nums">{points}</span>
      </div>
    </div>
  )
}

function Leaderboard() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const { data, isPending, isError } = useLeaderboard()

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-bold font-headline text-on-surface">{t('leaderboard.title')}</h1>
          <p className="text-sm text-on-surface-variant">
            {t('leaderboard.intro')}
          </p>
        </div>

        {isPending && (
          <p className="text-sm text-on-surface-variant">{t('leaderboard.loading')}</p>
        )}

        {isError && (
          <p role="alert" className="text-sm text-error">
            {t('leaderboard.loadFailed')}
          </p>
        )}

        {!isPending && !isError && data && (
          <>
            {data.callerRank && (
              <YourRankBanner rank={data.callerRank.rank} points={data.callerRank.points} />
            )}

            {!isAuthenticated && (
              <p className="text-xs text-on-surface-variant">
                <Trans
                  i18nKey="leaderboard.loginPrompt"
                  components={[<Link key="login-link" to="/login" className="text-primary font-semibold hover:underline" />]}
                />
              </p>
            )}

            {data.entries.length === 0 ? (
              <p className="text-sm text-on-surface-variant">
                {t('leaderboard.empty')}
              </p>
            ) : (
              <ol className="flex flex-col gap-2">
                {data.entries.map((entry) => (
                  <li
                    key={entry.userId}
                    className="bg-surface-container-lowest rounded-2xl shadow-sm p-3 flex items-center gap-3"
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
                      <span className="text-xs uppercase tracking-wide text-on-surface-variant">{t('leaderboard.points')}</span>
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
