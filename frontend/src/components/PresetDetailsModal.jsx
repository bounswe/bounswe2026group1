import { useEffect, useMemo, useRef, useState } from 'react'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import {
  CONSTRAINT_OBJECT_TYPES,
  MULTI_SURFACE_KEY,
  bucketKeyFor,
} from '../utils/routingConstraintObjects.js'

const TRAVEL_MODE_LABELS = {
  WALKING:    { label: 'Walking',    icon: 'directions_walk' },
  WHEELCHAIR: { label: 'Wheelchair', icon: 'accessible' },
}

const BUCKET_LABELS = {
  STAIR:               'Stairs',
  RAMP:                'Ramps',
  SIDEWALK:            'Sidewalks',
  CURB_RAMP:           'Curb ramps',
  PEDESTRIAN_CROSSING: 'Pedestrian crossings',
  [MULTI_SURFACE_KEY]: 'Multi-surface',
}

const BUCKET_ORDER = [
  'SIDEWALK', 'STAIR', 'RAMP', 'CURB_RAMP', 'PEDESTRIAN_CROSSING', MULTI_SURFACE_KEY,
]

function ObjectTypeBadge({ objectType }) {
  const cfg = OBJECT_TYPE_MAP[objectType]
  if (!cfg) return null
  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold text-white"
      style={{ backgroundColor: cfg.markerColor }}
      title={cfg.label}
    >
      <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>{cfg.icon}</span>
      {cfg.label}
    </span>
  )
}

/**
 * PresetDetailsModal
 * Read-only view of a preset (built-in or custom) — surfaces the constraint
 * list grouped by ObjectType so users can preview what a preset actually
 * does before activating it.
 *
 * Props:
 *  - title: string — preset display name
 *  - subtitle: string | null — extra context line (e.g. "Custom" or built-in label)
 *  - selectedConstraints: string[] — RoutingConstraint enum names
 *  - allConstraints: [{ name, label, description }] — backend catalog
 *  - travelMode: 'WALKING' | 'WHEELCHAIR' | null
 *  - onClose: () => void
 *  - onEdit: () => void — if provided, an "Edit" button is shown
 *  - onDelete: () => void — if provided, a "Delete" button is shown
 */
function PresetDetailsModal({
  title,
  subtitle,
  selectedConstraints,
  allConstraints,
  travelMode,
  onClose,
  onEdit,
  onDelete,
}) {
  const closeButtonRef = useRef(null)
  const tm = travelMode ? TRAVEL_MODE_LABELS[travelMode] : null
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const grouped = useMemo(() => {
    const selected = new Set(selectedConstraints ?? [])
    const buckets = new Map()
    for (const c of allConstraints ?? []) {
      if (!selected.has(c.name)) continue
      const key = bucketKeyFor(c.name)
      if (!buckets.has(key)) buckets.set(key, [])
      buckets.get(key).push(c)
    }
    return BUCKET_ORDER
      .filter(k => buckets.has(k))
      .map(k => ({ key: k, label: BUCKET_LABELS[k], items: buckets.get(k) }))
  }, [selectedConstraints, allConstraints])

  useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  function handleBackdropClick(event) {
    if (event.target === event.currentTarget) onClose()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`${title} details`}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-outline-variant">
          <div className="flex flex-col gap-0.5">
            <h2 className="text-xl font-bold font-headline text-on-surface">{title}</h2>
            {subtitle && (
              <span className="text-xs text-on-surface-variant">{subtitle}</span>
            )}
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-full p-1 hover:bg-surface-container cursor-pointer"
          >
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 flex flex-col gap-4">
          <div className="flex items-center gap-3 text-sm text-on-surface-variant">
            <span>
              {selectedConstraints?.length ?? 0}{' '}
              {(selectedConstraints?.length ?? 0) === 1 ? 'constraint' : 'constraints'}
            </span>
            {tm && (
              <span className="inline-flex items-center gap-1">
                <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>{tm.icon}</span>
                {tm.label}
              </span>
            )}
          </div>

          {grouped.length === 0 ? (
            <p className="text-sm text-on-surface-variant italic">
              This preset has no constraints — it routes with default behaviour.
            </p>
          ) : (
            grouped.map(group => (
              <div key={group.key} className="bg-surface-container-low rounded-xl p-3 flex flex-col gap-2">
                <div className="flex items-center gap-2 px-1">
                  {group.key !== MULTI_SURFACE_KEY && OBJECT_TYPE_MAP[group.key] && (
                    <span
                      className="material-symbols-outlined"
                      style={{ color: OBJECT_TYPE_MAP[group.key].markerColor, fontSize: '20px' }}
                    >
                      {OBJECT_TYPE_MAP[group.key].icon}
                    </span>
                  )}
                  <span className="text-sm font-bold text-on-surface">{group.label}</span>
                </div>
                <ul className="flex flex-col gap-2">
                  {group.items.map(c => {
                    const types = CONSTRAINT_OBJECT_TYPES[c.name] || []
                    return (
                      <li key={c.name} className="flex flex-col gap-1 p-2 rounded-lg">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-semibold text-on-surface">{c.label}</span>
                          {types.length > 1 && types.map(t => (
                            <ObjectTypeBadge key={t} objectType={t} />
                          ))}
                        </div>
                        <span className="text-xs text-on-surface-variant">{c.description}</span>
                      </li>
                    )
                  })}
                </ul>
              </div>
            ))
          )}
        </div>

        <div className="flex items-center justify-between gap-2 p-5 border-t border-outline-variant">
          {onDelete ? (
            confirmingDelete ? (
              <div className="flex items-center gap-2" role="group" aria-label="Confirm deletion">
                <span className="text-sm text-error font-semibold hidden sm:inline">Delete this preset?</span>
                <button
                  type="button"
                  onClick={() => { setConfirmingDelete(false); onDelete() }}
                  className="px-4 py-2 rounded-lg bg-error text-on-error font-semibold cursor-pointer"
                >
                  Confirm delete
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmingDelete(true)}
                className="px-4 py-2 rounded-lg bg-error/10 text-error font-semibold cursor-pointer"
              >
                Delete
              </button>
            )
          ) : <span />}
          <div className="flex gap-2">
            {onEdit && !confirmingDelete && (
              <button
                type="button"
                onClick={onEdit}
                className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer"
              >
                Edit
              </button>
            )}
            {!confirmingDelete && (
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-lg bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-semibold cursor-pointer"
              >
                Close
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default PresetDetailsModal
