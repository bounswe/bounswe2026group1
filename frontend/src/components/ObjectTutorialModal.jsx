import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { OBJECT_TYPE_MAP, localizeObjectType } from '../utils/objectTypeConfig.js'

/**
 * ObjectTutorialModal
 * Just-in-time accessibility tutorial shown from inside an object card on
 * the create-report flow. Pulls measurement metadata + plain-language
 * descriptions straight from objectTypeConfig.js so authors only edit
 * one file when the standards change.
 *
 * Props:
 *  - objectType: 'RAMP' | 'ELEVATOR' | … (must exist in OBJECT_TYPE_MAP)
 *  - onClose:    () => void
 */
function ObjectTutorialModal({ objectType, onClose }) {
  const { t } = useTranslation()
  const cfg = localizeObjectType(t, OBJECT_TYPE_MAP[objectType])
  const closeButtonRef = useRef(null)

  useEffect(() => {
    function handleKey(e) { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  function handleBackdrop(e) {
    if (e.target === e.currentTarget) onClose()
  }

  if (!cfg) return null
  if (typeof document === 'undefined') return null

  function thresholdText(m) {
    if (m.accessible_min !== undefined && m.accessible_max !== undefined) {
      return t('createReport.thresholdRange', { min: m.accessible_min, max: m.accessible_max, unit: m.unit })
    }
    if (m.accessible_min !== undefined) return t('createReport.thresholdMin', { min: m.accessible_min, unit: m.unit })
    if (m.accessible_max !== undefined) return t('createReport.thresholdMax', { max: m.accessible_max, unit: m.unit })
    return null
  }

  // Portal to body so the modal doesn't inherit `pointer-events-none`
  // from CreateReportPanel's outer wrapper (which lets users click the
  // map behind the bottom-sheet) — without this the X close button
  // wouldn't receive clicks.
  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t('createReport.howToMeasureAria', { label: cfg.label })}
      onClick={handleBackdrop}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-surface-container-high rounded-2xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-outline-variant/20">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined" style={{ color: cfg.markerColor }}>{cfg.icon}</span>
            <h3 className="text-base font-bold font-headline text-on-surface">
              {t('createReport.howToMeasure')}: {cfg.label}
            </h3>
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label={t('onboarding.closeTutorial')}
            className="w-9 h-9 rounded-full hover:bg-surface-container flex items-center justify-center cursor-pointer"
          >
            <span className="material-symbols-outlined text-on-surface-variant">close</span>
          </button>
        </div>

        <div className="p-5 flex flex-col gap-4">
          {cfg.measurements.length === 0 ? (
            <p className="text-sm text-on-surface-variant">
              {t('createReport.howToMeasureEmpty')}
            </p>
          ) : (
            <ul className="flex flex-col gap-4">
              {cfg.measurements.map((m) => {
                const threshold = thresholdText(m)
                return (
                  <li key={m.key} className="flex flex-col gap-1">
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-sm font-bold text-on-surface">{m.label}</span>
                      <span className="text-xs text-on-surface-variant">({m.unit})</span>
                      {threshold && (
                        <span className="text-xs font-semibold text-primary">{threshold}</span>
                      )}
                    </div>
                    {m.description && (
                      <p className="text-sm text-on-surface-variant leading-relaxed">
                        {m.description}
                      </p>
                    )}
                  </li>
                )
              })}
            </ul>
          )}

          <p className="text-[11px] text-on-surface-variant pt-2 border-t border-outline-variant/15">
            {t('createReport.howToMeasureFooter')}
          </p>
        </div>
      </div>
    </div>,
    document.body,
  )
}

export default ObjectTutorialModal
