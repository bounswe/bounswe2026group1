import { useCallback, useState } from 'react'
import Navbar from '../components/Navbar.jsx'
import Toast from '../components/Toast.jsx'
import AvatarUploader from '../components/AvatarUploader.jsx'
import BadgeList from '../components/BadgeList.jsx'
import MyReportsSection from '../components/MyReportsSection.jsx'
import RoutingPreferencesSection from '../components/RoutingPreferencesSection.jsx'
import DangerZoneSection from '../components/DangerZoneSection.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useCurrentUser } from '../hooks/useCurrentUser.js'
import { useDeleteAvatar, useUpdateProfile, useUploadAvatar } from '../hooks/useProfile.js'
import { useSetLeaderboardVisibility } from '../hooks/useGamification.js'

const NAME_MIN = 2
const NAME_MAX = 50
const BIO_MAX = 500

function StatTile({ label, value }) {
  return (
    <div className="flex-1 min-w-[140px] bg-surface-container-low rounded-2xl p-4 flex flex-col gap-1">
      <span className="text-xs uppercase tracking-wide text-on-surface-variant">{label}</span>
      <span className="text-2xl font-bold text-on-surface">{value}</span>
    </div>
  )
}

function ProfilePage() {
  const { userId } = useAuth()
  const { data: user, isPending, isError, error } = useCurrentUser()
  const updateProfileMutation = useUpdateProfile()
  const uploadAvatarMutation = useUploadAvatar()
  const deleteAvatarMutation = useDeleteAvatar()
  const setVisibilityMutation = useSetLeaderboardVisibility()

  const [isEditing, setIsEditing] = useState(false)
  const [name, setName] = useState('')
  const [bio, setBio] = useState('')
  const [formError, setFormError] = useState('')
  const [toast, setToast] = useState(null)

  const handleToastDismiss = useCallback(() => setToast(null), [])

  const trimmedName = name.trim()
  const nameInvalid = trimmedName.length < NAME_MIN || trimmedName.length > NAME_MAX
  const bioInvalid = bio.length > BIO_MAX
  const saveDisabled = updateProfileMutation.isPending || nameInvalid || bioInvalid

  function handleStartEdit() {
    if (!user) return
    setName(user.name ?? '')
    setBio(user.bio ?? '')
    setFormError('')
    setIsEditing(true)
  }

  function handleCancel() {
    setFormError('')
    setIsEditing(false)
  }

  async function handleSave() {
    setFormError('')
    try {
      await updateProfileMutation.mutateAsync({ name: trimmedName, bio })
      setIsEditing(false)
      setToast({ message: 'Profile updated.', type: 'success' })
    } catch (e) {
      setFormError(e.message || 'Failed to update profile.')
    }
  }

  async function handleAvatarUpload(file) {
    await uploadAvatarMutation.mutateAsync(file)
    setToast({ message: 'Avatar updated.', type: 'success' })
  }

  async function handleAvatarDelete() {
    await deleteAvatarMutation.mutateAsync()
    setToast({ message: 'Avatar removed.', type: 'success' })
  }

  async function handleVisibilityToggle(event) {
    const hidden = event.target.checked
    try {
      await setVisibilityMutation.mutateAsync(hidden)
      setToast({
        message: hidden
          ? 'You are now hidden from the public leaderboard.'
          : 'You are visible on the public leaderboard.',
        type: 'success',
      })
    } catch (e) {
      setToast({ message: e.message || 'Failed to update visibility.', type: 'error' })
    }
  }

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        {isPending && (
          <p className="text-sm text-on-surface-variant">Loading your profile…</p>
        )}

        {isError && (
          <p role="alert" className="text-sm text-error">
            Failed to load profile{error?.message ? `: ${error.message}` : '.'}
          </p>
        )}

        {!isPending && !isError && user && (
          <>
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col gap-4">
              <div className="flex items-start gap-4 flex-wrap">
                <div className="flex-1 min-w-0">
                  <h1 className="text-2xl font-bold font-headline text-on-surface break-words">
                    {user.name}
                  </h1>
                  <p className="text-sm text-on-surface-variant break-all">{user.email}</p>
                  <span className="inline-block mt-2 text-xs font-bold px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                    {user.role}
                  </span>
                </div>
              </div>

              <AvatarUploader
                currentAvatarUrl={user.avatarUrl}
                isUploading={uploadAvatarMutation.isPending}
                isDeleting={deleteAvatarMutation.isPending}
                onUpload={handleAvatarUpload}
                onDelete={handleAvatarDelete}
              />
            </section>

            <section className="flex gap-3 flex-wrap">
              <StatTile label="Reports submitted" value={user.contributionStats?.reportsSubmitted ?? 0} />
              <StatTile label="Routes planned" value={user.contributionStats?.routesPlanned ?? 0} />
              <StatTile label="Points" value={user.points ?? 0} />
              <StatTile
                label="Rank"
                value={user.rank != null ? `#${user.rank}` : user.leaderboardHidden ? 'Hidden' : '—'}
              />
            </section>

            {user.badges && user.badges.length > 0 && (
              <section className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col gap-3">
                <h2 className="text-xl font-bold font-headline text-on-surface">Badges</h2>
                <BadgeList badges={user.badges} />
              </section>
            )}

            <section className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col gap-3">
              <h2 className="text-xl font-bold font-headline text-on-surface">Leaderboard</h2>
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={user.leaderboardHidden}
                  onChange={handleVisibilityToggle}
                  disabled={setVisibilityMutation.isPending}
                  className="w-5 h-5 accent-primary cursor-pointer disabled:opacity-60"
                />
                <span className="text-sm text-on-surface">
                  Hide me from the public leaderboard
                </span>
              </label>
              <p className="text-xs text-on-surface-variant">
                Your points are preserved either way — you re-enter the ranking
                instantly when this flips back.
              </p>
            </section>

            <section className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-bold font-headline text-on-surface">About</h2>
                {!isEditing && (
                  <button
                    type="button"
                    onClick={handleStartEdit}
                    className="px-3 py-1.5 rounded-lg bg-surface-container text-on-surface font-semibold text-sm cursor-pointer"
                  >
                    Edit
                  </button>
                )}
              </div>

              {!isEditing ? (
                <p className="text-sm text-on-surface whitespace-pre-wrap break-words">
                  {user.bio || <em className="text-on-surface-variant">No bio yet.</em>}
                </p>
              ) : (
                <div className="flex flex-col gap-4">
                  <div className="flex flex-col gap-1">
                    <label htmlFor="profile-name" className="text-sm font-bold text-on-surface-variant px-1">
                      Display name
                    </label>
                    <input
                      id="profile-name"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      maxLength={NAME_MAX}
                      className="w-full px-5 py-3 bg-surface-container border-none rounded-xl text-on-surface focus:ring-2 focus:ring-primary/40 outline-none"
                    />
                    <p className={`text-xs text-right ${nameInvalid ? 'text-error' : 'text-on-surface-variant'}`}>
                      {trimmedName.length < NAME_MIN
                        ? `Name must be at least ${NAME_MIN} characters.`
                        : `${name.length}/${NAME_MAX}`}
                    </p>
                  </div>

                  <div className="flex flex-col gap-1">
                    <label htmlFor="profile-bio" className="text-sm font-bold text-on-surface-variant px-1">
                      Bio
                    </label>
                    <textarea
                      id="profile-bio"
                      value={bio}
                      onChange={(e) => setBio(e.target.value)}
                      rows={4}
                      className="w-full px-5 py-3 bg-surface-container border-none rounded-xl text-on-surface focus:ring-2 focus:ring-primary/40 outline-none"
                    />
                    <p className={`text-xs text-right ${bioInvalid ? 'text-error' : 'text-on-surface-variant'}`}>
                      {bio.length}/{BIO_MAX}
                    </p>
                  </div>

                  {formError && (
                    <p role="alert" className="text-sm text-error">
                      {formError}
                    </p>
                  )}

                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={handleSave}
                      disabled={saveDisabled}
                      className="px-4 py-2 rounded-lg primary-gradient text-on-primary font-semibold disabled:opacity-60 cursor-pointer"
                    >
                      {updateProfileMutation.isPending ? 'Saving…' : 'Save'}
                    </button>
                    <button
                      type="button"
                      onClick={handleCancel}
                      disabled={updateProfileMutation.isPending}
                      className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </section>

            <RoutingPreferencesSection />

            <MyReportsSection userId={userId} />

            <DangerZoneSection email={user.email} />
          </>
        )}
      </main>

      {toast && (
        <Toast message={toast.message} type={toast.type} onDismiss={handleToastDismiss} />
      )}
    </div>
  )
}

export default ProfilePage
