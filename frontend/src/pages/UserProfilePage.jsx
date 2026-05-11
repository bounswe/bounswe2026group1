import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Navbar from '../components/Navbar.jsx'
import BadgeList from '../components/BadgeList.jsx'
import MyReportsSection from '../components/MyReportsSection.jsx'
import { useUserProfile } from '../hooks/useUserProfile.js'

function StatTile({ label, value }) {
  return (
    <div className="flex-1 min-w-[140px] bg-surface-container-low rounded-2xl p-4 flex flex-col gap-1">
      <span className="text-xs uppercase tracking-wide text-on-surface-variant">{label}</span>
      <span className="text-2xl font-bold text-on-surface">{value}</span>
    </div>
  )
}

function UserProfilePage() {
  const { t } = useTranslation()
  const { userId } = useParams()
  const navigate = useNavigate()
  const { data: user, isPending, isError } = useUserProfile(userId ? Number(userId) : undefined)

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        {isPending && (
          <p className="text-sm text-on-surface-variant">{t('profile.loadingOther')}</p>
        )}

        {isError && (
          <div className="flex flex-col gap-3 items-start">
            <p role="alert" className="text-sm text-error">{t('profile.userNotFound')}</p>
            <button
              type="button"
              onClick={() => navigate(-1)}
              className="text-sm text-primary font-semibold hover:underline cursor-pointer"
            >
              {t('profile.goBack')}
            </button>
          </div>
        )}

        {!isPending && !isError && user && (
          <>
            <section className="bg-white rounded-2xl shadow-sm p-6 flex flex-col gap-4">
              <div className="flex items-start gap-4 flex-wrap">
                <div className="w-16 h-16 rounded-full bg-surface-container-high flex items-center justify-center flex-shrink-0 overflow-hidden">
                  {user.avatarUrl ? (
                    <img
                      src={user.avatarUrl}
                      alt={user.name}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '32px' }}>
                      person
                    </span>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <h1 className="text-2xl font-bold font-headline text-on-surface break-words">
                    {user.name}
                  </h1>
                  <span className="inline-block mt-2 text-xs font-bold px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                    {t(`profile.role.${user.role}`, { defaultValue: user.role })}
                  </span>
                </div>
              </div>
            </section>

            <section className="flex gap-3 flex-wrap">
              <StatTile label={t('profile.reportsSubmitted')} value={user.contributionStats?.reportsSubmitted ?? 0} />
              <StatTile label={t('profile.routesPlanned')} value={user.contributionStats?.routesPlanned ?? 0} />
              <StatTile label={t('profile.points')} value={user.points ?? 0} />
              {/* Rank is omitted entirely when the user has opted out, instead
                  of showing "Hidden" — that detail is private to the owner. */}
              {!user.leaderboardHidden && user.rank != null && (
                <StatTile label={t('profile.rank')} value={`#${user.rank}`} />
              )}
            </section>

            {user.badges && user.badges.length > 0 && (
              <section className="bg-white rounded-2xl shadow-sm p-6 flex flex-col gap-3">
                <h2 className="text-xl font-bold font-headline text-on-surface">{t('profile.badges')}</h2>
                <BadgeList badges={user.badges} />
              </section>
            )}

            <section className="bg-white rounded-2xl shadow-sm p-6 flex flex-col gap-4">
              <h2 className="text-xl font-bold font-headline text-on-surface">{t('profile.about')}</h2>
              <p className="text-sm text-on-surface whitespace-pre-wrap break-words">
                {user.bio || <em className="text-on-surface-variant">{t('profile.noBio')}</em>}
              </p>
            </section>

            <MyReportsSection userId={Number(userId)} isOwnProfile={false} />
          </>
        )}
      </main>
    </div>
  )
}

export default UserProfilePage
