import BadgeIcon from './BadgeIcon.jsx'

/**
 * Horizontal chip strip of all badges held by a user. Profile pages render
 * this above contribution stats. Renders nothing when the list is empty —
 * "no badges yet" copy is the caller's responsibility.
 */
function BadgeList({ badges }) {
  if (!badges || badges.length === 0) return null

  return (
    <div className="flex flex-wrap gap-2" aria-label="User badges">
      {badges.map((badge) => (
        <BadgeIcon key={badge} badge={badge} />
      ))}
    </div>
  )
}

export default BadgeList
