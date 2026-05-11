/** Chip styling for feed filters and matching action buttons (primary outline style). */
export function feedFilterChipClass(active) {
  return [
    'inline-flex items-center justify-center px-4 py-2 rounded-2xl text-sm font-semibold border transition-colors',
    'focus:outline-none focus-visible:ring-2 focus-visible:ring-primary',
    active
      ? 'bg-primary text-on-primary border-primary shadow-sm'
      : 'bg-surface-container-highest text-primary dark:text-on-surface border-primary/30 hover:bg-primary/10 hover:border-primary/45',
  ].join(' ')
}
