function StatCard({ label, value, icon, accent = 'primary' }) {
  const accentClass =
    accent === 'error'
      ? 'bg-error-container text-on-error-container'
      : accent === 'tertiary'
        ? 'bg-tertiary-container text-on-tertiary-container'
        : accent === 'secondary'
          ? 'bg-secondary-container text-on-secondary-container'
          : 'bg-primary-container text-on-primary-container'

  return (
    <div className="flex items-center gap-4 p-5 rounded-2xl bg-surface-container-lowest border border-outline-variant">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 ${accentClass}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="min-w-0">
        <div className="text-sm font-medium text-on-surface-variant truncate">{label}</div>
        <div className="text-2xl font-bold font-headline text-on-surface tabular-nums">
          {value ?? '—'}
        </div>
      </div>
    </div>
  )
}

export default StatCard
