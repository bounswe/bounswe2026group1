import { useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import { contributeMeasurements, mapReport } from '../services/reportService.js'

/**
 * Bottom-sheet modal for contributing missing measurements to a single report object.
 * Uses PATCH /api/reports/{reportId}/objects/{objectId}/measurements — any authenticated
 * user may call it, but already-populated keys are protected (409 Conflict).
 */
export default function ContributeMeasurementsPanel({ report, object, onClose, onContributed }) {
  const { token } = useAuth()
  const cfg = OBJECT_TYPE_MAP[object.objectType]

  const blankFields = (cfg?.measurements ?? []).filter(m => {
    const v = object.measurements?.[m.key]
    return v === undefined || v === null || v === ''
  })

  const [values, setValues] = useState(() =>
    Object.fromEntries(blankFields.map(m => [m.key, '']))
  )
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit() {
    const filled = Object.fromEntries(
      Object.entries(values).filter(([, v]) => v !== '')
    )
    if (Object.keys(filled).length === 0) {
      setError('Fill in at least one measurement.')
      return
    }

    const numeric = Object.fromEntries(
      Object.entries(filled).map(([k, v]) => [k, parseFloat(v)])
    )

    setSubmitting(true)
    setError('')
    try {
      const updated = await contributeMeasurements(report.id, object.id, numeric, token)
      onContributed(mapReport(updated))
      onClose()
    } catch (err) {
      setError(err.message || 'Failed to save measurements. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-[2100] flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="bg-surface w-full sm:max-w-sm rounded-t-2xl sm:rounded-2xl shadow-xl flex flex-col gap-5 p-6"
        onClick={e => e.stopPropagation()}
      >
        {/* Drag handle visible on small screens */}
        <div className="w-10 h-1 rounded-full bg-outline-variant/40 mx-auto sm:hidden" />

        {/* Header */}
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
            <span
              className="material-symbols-outlined text-primary"
              style={{ fontSize: '20px', fontVariationSettings: "'FILL' 1" }}
            >
              {cfg?.icon ?? 'category'}
            </span>
          </div>
          <div className="min-w-0">
            <p className="font-bold text-on-surface">Add missing measurements</p>
            <p className="text-sm text-on-surface-variant mt-0.5 truncate">
              {cfg?.label ?? object.objectType}
              <span className="font-normal opacity-60"> · only blank fields can be filled</span>
            </p>
          </div>
        </div>

        {blankFields.length === 0 ? (
          <p className="text-sm text-on-surface-variant text-center py-6">
            All measurements are already filled in for this object.
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            {blankFields.map(field => (
              <div key={field.key}>
                <label className="text-sm font-semibold text-on-surface block mb-1.5">
                  {field.label}
                  {field.unit && (
                    <span className="font-normal text-on-surface-variant"> ({field.unit})</span>
                  )}
                </label>
                <input
                  type="number"
                  min="0"
                  value={values[field.key]}
                  onChange={e => setValues(prev => ({ ...prev, [field.key]: e.target.value }))}
                  placeholder={`${field.label} in ${field.unit}`}
                  className="w-full rounded-xl border border-outline-variant/30 bg-surface-container px-4 py-3 text-sm text-on-surface placeholder-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                {field.description && (
                  <p className="text-xs text-on-surface-variant mt-1.5 leading-snug opacity-70">
                    {field.description}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}

        {error && (
          <p role="alert" className="text-sm text-error bg-error/8 rounded-lg px-3 py-2.5">
            {error}
          </p>
        )}

        <div className="flex gap-3 pt-1">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="flex-1 py-3 rounded-xl bg-surface-container text-on-surface font-semibold text-sm transition-colors hover:bg-surface-container-high"
          >
            Cancel
          </button>
          {blankFields.length > 0 && (
            <button
              type="button"
              onClick={handleSubmit}
              disabled={submitting}
              className="flex-1 py-3 rounded-xl bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-semibold text-sm disabled:opacity-60 transition-opacity"
            >
              {submitting ? 'Saving…' : 'Submit'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
