const TONE_CLASSES = {
  neutral: 'bg-surface-container text-on-surface-variant',
  success: 'bg-primary-container text-on-primary-container',
  warning: 'bg-tertiary-container text-on-tertiary-container',
  danger: 'bg-error-container text-on-error-container',
  info: 'bg-secondary-container text-on-secondary-container',
}

function Badge({ tone = 'neutral', children }) {
  const classes = TONE_CLASSES[tone] ?? TONE_CLASSES.neutral
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${classes}`}
    >
      {children}
    </span>
  )
}

export default Badge
