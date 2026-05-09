// Material Symbols icon + label mapping for each backend Badge enum value.
// Keep in sync with com.bounswe2026group1.backend.model.Badge — adding a new
// badge there means adding an entry here.
const BADGE_META = {
  TRUSTED_REPORTER: {
    label: 'Trusted Reporter',
    icon: 'verified',
    description: '10 verified reports',
    tone: 'bg-tertiary/10 text-tertiary',
  },
  EXPERT_MAPPER: {
    label: 'Expert Mapper',
    icon: 'workspace_premium',
    description: '50 verified reports',
    tone: 'bg-secondary/15 text-secondary',
  },
  TOP_10: {
    label: 'Top 10',
    icon: 'emoji_events',
    description: 'Currently in the top 10 leaderboard',
    tone: 'bg-primary/15 text-primary',
  },
}

/**
 * Single-badge chip used in profile pages and inline next to author names.
 * Sizes: 'sm' (compact, just the icon), 'md' (default chip with label).
 */
function BadgeIcon({ badge, size = 'md' }) {
  const meta = BADGE_META[badge]
  if (!meta) return null

  if (size === 'sm') {
    return (
      <span
        title={`${meta.label} — ${meta.description}`}
        className={`inline-flex items-center justify-center w-6 h-6 rounded-full ${meta.tone}`}
        aria-label={meta.label}
      >
        <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
          {meta.icon}
        </span>
      </span>
    )
  }

  return (
    <span
      title={meta.description}
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${meta.tone}`}
    >
      <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
        {meta.icon}
      </span>
      {meta.label}
    </span>
  )
}

export default BadgeIcon
