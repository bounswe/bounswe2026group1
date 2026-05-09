import { useTheme } from '../context/ThemeContext.jsx'

// Compact icon-only theme toggle for the navbar. Cycles
// System → Light → Dark → System on click; the icon reflects the
// current mode and the tooltip names the next mode. The full
// segmented "Appearance" control is exported as ThemePickerSegmented
// for use in settings surfaces.

const ORDER = ['system', 'light', 'dark']
const META = {
  system: { icon: 'brightness_auto', label: 'System' },
  light:  { icon: 'light_mode',      label: 'Light' },
  dark:   { icon: 'dark_mode',       label: 'Dark' },
}

function ThemeToggleButton({ className = '' }) {
  const { mode, setMode } = useTheme()
  const { icon, label } = META[mode]
  const next = ORDER[(ORDER.indexOf(mode) + 1) % ORDER.length]
  return (
    <button
      type="button"
      onClick={() => setMode(next)}
      title={`Theme: ${label} (click for ${META[next].label})`}
      aria-label={`Theme: ${label}. Click to switch to ${META[next].label}.`}
      className={
        'shrink-0 w-9 h-9 md:w-10 md:h-10 rounded-full flex items-center justify-center hover:bg-surface-container transition-colors ' +
        className
      }
    >
      <span className="material-symbols-outlined text-on-surface-variant" aria-hidden="true">
        {icon}
      </span>
    </button>
  )
}

export function ThemePickerSegmented({ className = '' }) {
  const { mode, setMode } = useTheme()
  return (
    <div className={className} role="group" aria-label="Theme">
      <p className="text-[11px] font-bold uppercase tracking-wider text-on-surface-variant px-1 pb-2">
        Appearance
      </p>
      <div className="flex items-center gap-1 p-1 rounded-xl bg-surface-container">
        {ORDER.map((m) => {
          const selected = mode === m
          return (
            <button
              key={m}
              type="button"
              onClick={() => setMode(m)}
              aria-pressed={selected}
              className={
                'flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-semibold transition-colors ' +
                (selected
                  ? 'bg-surface-container-lowest text-primary shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface')
              }
            >
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                {META[m].icon}
              </span>
              {META[m].label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export default ThemeToggleButton
