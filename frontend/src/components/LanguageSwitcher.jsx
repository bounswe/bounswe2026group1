import { useTranslation } from 'react-i18next'
import { useSetLanguage } from '../hooks/useSetLanguage.js'

/**
 * Compact two-button toggle for switching the UI language.
 * Mounted in the Navbar next to the theme picker.
 */
function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  const setLanguage = useSetLanguage()
  const current = (i18n.resolvedLanguage || 'en').toLowerCase()

  function onSelect(code) {
    if (code === current || setLanguage.isPending) return
    setLanguage.mutate(code.toUpperCase())
  }

  return (
    <div
      role="group"
      aria-label={t('common.language', 'Language')}
      className="flex items-center rounded-full bg-surface-container-low p-0.5 text-xs font-bold"
    >
      <button
        type="button"
        onClick={() => onSelect('en')}
        aria-pressed={current === 'en'}
        className={`px-2.5 py-1 rounded-full transition-colors cursor-pointer ${
          current === 'en'
            ? 'bg-primary text-on-primary'
            : 'text-on-surface-variant hover:text-primary'
        }`}
      >
        EN
      </button>
      <button
        type="button"
        onClick={() => onSelect('tr')}
        aria-pressed={current === 'tr'}
        className={`px-2.5 py-1 rounded-full transition-colors cursor-pointer ${
          current === 'tr'
            ? 'bg-primary text-on-primary'
            : 'text-on-surface-variant hover:text-primary'
        }`}
      >
        TR
      </button>
    </div>
  )
}

export default LanguageSwitcher
