import { useTranslation } from 'react-i18next'
import { OBJECT_TYPE_MAP, localizeObjectType } from '../utils/objectTypeConfig.js'

/**
 * Renders the multi-object section of a mapped report (icon + label per object,
 * issue chips, measurement chips with accessibility-threshold warn/ok indicators).
 * Expects the shape produced by mapReport(): objects[].measurements is a parsed
 * object (not a JSON string) and objects[].issues is an array of keys.
 */
function ReportObjectsList({ objects }) {
  const { t } = useTranslation()
  if (!objects?.length) return null

  return (
    <section className="flex flex-col gap-3">
      <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">{t('report.objects')}</h3>
      {objects.map((obj, i) => {
        const cfg = localizeObjectType(t, OBJECT_TYPE_MAP[obj.objectType])
        return (
          <div key={i} className="bg-surface-container-lowest rounded-xl border border-outline-variant/10 p-4 flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
                <span className="material-symbols-outlined text-primary" style={{ fontSize: '18px', fontVariationSettings: "'FILL' 1" }}>
                  {cfg?.icon ?? 'category'}
                </span>
              </div>
              <span className="text-sm font-bold text-on-surface">{cfg?.label ?? obj.objectType}</span>
            </div>

            {obj.issues?.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {obj.issues.map(issueKey => (
                  <span key={issueKey} className="flex items-center gap-1 text-[11px] font-semibold px-2.5 py-1 rounded-full bg-error/10 text-error border border-error/15">
                    <span className="material-symbols-outlined" style={{ fontSize: '12px' }}>warning</span>
                    {cfg?.issues.find(i => i.key === issueKey)?.label ?? issueKey.replace(/_/g, ' ')}
                  </span>
                ))}
              </div>
            )}

            {obj.measurements && Object.keys(obj.measurements).length > 0 && (
              <div className="flex flex-wrap gap-2">
                {Object.entries(obj.measurements).map(([key, val]) => {
                  const schema = cfg?.measurements.find(m => m.key === key)
                  const numVal = parseFloat(val)
                  const isWarn = schema && (
                    (schema.accessible_max !== undefined && numVal > schema.accessible_max) ||
                    (schema.accessible_min !== undefined && numVal < schema.accessible_min)
                  )
                  return (
                    <span key={key} className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg flex items-center gap-1 ${
                      isWarn
                        ? 'bg-error/10 text-error border border-error/15'
                        : 'bg-primary/10 text-primary border border-primary/15'
                    }`}>
                      <span className="material-symbols-outlined" style={{ fontSize: '12px' }}>
                        {isWarn ? 'close' : 'check'}
                      </span>
                      {schema?.label ?? key}: {val} {schema?.unit ?? ''}
                      {schema && (schema.accessible_min !== undefined || schema.accessible_max !== undefined) && (
                        <span className="opacity-60 font-normal ml-0.5">
                          ({schema.accessible_min !== undefined ? `≥${schema.accessible_min}` : `≤${schema.accessible_max}`} ok)
                        </span>
                      )}
                    </span>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}
    </section>
  )
}

export default ReportObjectsList
