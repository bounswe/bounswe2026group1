import { useEffect, useMemo, useRef, useState } from 'react'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import {
  CONSTRAINT_OBJECT_TYPES,
  MULTI_SURFACE_KEY,
  bucketKeyFor,
} from '../utils/routingConstraintObjects.js'

const NAME_MIN = 1
const NAME_MAX = 50

const TRAVEL_MODES = [
  { value: 'WALKING',    label: 'Walking' },
  { value: 'WHEELCHAIR', label: 'Wheelchair' },
]

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
 * CustomPresetEditorModal
 * Props:
 *  - mode: 'create' | 'edit'
 *  - profile: existing profile (when editing); null when creating
 *  - availableConstraints: [{ name, label, description }, ...]
 *  - onClose: () => void
 *  - onSave: ({ name, constraints, preferredTravelMode }) => Promise
 *  - onDelete: () => Promise (only used in edit mode)
 *  - busy: boolean — disables buttons during async work
 *  - errorMessage: string | null — server-side error to surface
 */
function CustomPresetEditorModal({
  mode,
  profile,
  availableConstraints,
  onClose,
  onSave,
  onDelete,
  busy,
  errorMessage,
}) {
  const isEdit = mode === 'edit'
  const closeButtonRef = useRef(null)

  const [name, setName] = useState(profile?.name ?? '')
  const [constraints, setConstraints] = useState(() => new Set(profile?.constraints ?? []))
  // Custom presets always carry an explicit travel mode — null on an existing
  // record (legacy data) is normalised to WALKING so the radio always has a
  // selected option.
  const [travelMode, setTravelMode] = useState(profile?.preferredTravelMode ?? 'WALKING')
  const [localError, setLocalError] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  // Group constraints by ObjectType bucket once, so the rendering loop is cheap.
  const grouped = useMemo(() => {
    const buckets = new Map()
    for (const c of availableConstraints) {
      const key = bucketKeyFor(c.name)
      if (!buckets.has(key)) buckets.set(key, [])
      buckets.get(key).push(c)
    }
    return BUCKET_ORDER
      .filter(k => buckets.has(k))
      .map(k => ({ key: k, label: BUCKET_LABELS[k], items: buckets.get(k) }))
  }, [availableConstraints])

  useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    document.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose, busy])

  function handleBackdropClick(event) {
    if (event.target === event.currentTarget && !busy) onClose()
  }

  function toggleConstraint(name) {
    setConstraints(prev => {
      const next = new Set(prev)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  const trimmedName = name.trim()
  const nameInvalid = trimmedName.length < NAME_MIN || trimmedName.length > NAME_MAX
  const saveDisabled = busy || nameInvalid

  async function handleSave() {
    setLocalError('')
    if (nameInvalid) {
      setLocalError(`Name must be ${NAME_MIN}–${NAME_MAX} characters.`)
      return
    }
    try {
      await onSave({
        name: trimmedName,
        constraints: Array.from(constraints),
        preferredTravelMode: travelMode,
      })
    } catch (e) {
      setLocalError(e.message || 'Failed to save.')
    }
  }

  async function handleDelete() {
    setLocalError('')
    setConfirmingDelete(false)
    try {
      await onDelete()
    } catch (e) {
      setLocalError(e.message || 'Failed to delete.')
    }
  }

  const visibleError = localError || errorMessage

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={isEdit ? 'Edit custom routing preset' : 'Create custom routing preset'}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-outline-variant">
          <h2 className="text-xl font-bold font-headline text-on-surface">
            {isEdit ? 'Edit custom preset' : 'New custom preset'}
          </h2>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            disabled={busy}
            aria-label="Close"
            className="rounded-full p-1 hover:bg-surface-container disabled:opacity-50 cursor-pointer"
          >
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            <label htmlFor="preset-name" className="text-sm font-bold text-on-surface px-1">
              Preset name
            </label>
            <input
              id="preset-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={NAME_MAX}
              placeholder="e.g. Work commute"
              className="w-full px-4 py-2.5 bg-surface-container border-none rounded-xl focus:ring-2 focus:ring-primary/40"
            />
            <p className={`text-xs text-right ${nameInvalid ? 'text-error' : 'text-on-surface-variant'}`}>
              {trimmedName.length === 0
                ? `Required (${NAME_MIN}–${NAME_MAX} characters)`
                : `${trimmedName.length}/${NAME_MAX}`}
            </p>
          </div>

          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-bold text-on-surface px-1 mb-1">Travel mode</legend>
            <div className="flex gap-3 flex-wrap">
              {TRAVEL_MODES.map(tm => (
                <label key={tm.value} className="inline-flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="travel-mode"
                    value={tm.value}
                    checked={travelMode === tm.value}
                    onChange={() => setTravelMode(tm.value)}
                  />
                  <span className="text-sm">{tm.label}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-bold text-on-surface px-1">Constraints</h3>
            <p className="text-xs text-on-surface-variant px-1 -mt-1">
              Pick the hazards you want routes to avoid. Each constraint covers one or more report types.
            </p>
            {grouped.map(group => (
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
                    const checked = constraints.has(c.name)
                    return (
                      <li key={c.name}>
                        <label className="flex items-start gap-3 cursor-pointer p-2 rounded-lg hover:bg-surface-container">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleConstraint(c.name)}
                            className="mt-1"
                          />
                          <div className="flex-1 flex flex-col gap-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-sm font-semibold text-on-surface">{c.label}</span>
                              {types.length > 1 && types.map(t => (
                                <ObjectTypeBadge key={t} objectType={t} />
                              ))}
                            </div>
                            <span className="text-xs text-on-surface-variant">{c.description}</span>
                          </div>
                        </label>
                      </li>
                    )
                  })}
                </ul>
              </div>
            ))}
          </div>

          {visibleError && (
            <p role="alert" className="text-sm text-error">{visibleError}</p>
          )}
        </div>

        <div className="flex items-center justify-between gap-2 p-5 border-t border-outline-variant">
          {isEdit ? (
            confirmingDelete ? (
              <div className="flex items-center gap-2" role="group" aria-label="Confirm deletion">
                <span className="text-sm text-error font-semibold hidden sm:inline">Delete this preset?</span>
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={busy}
                  className="px-4 py-2 rounded-lg bg-error text-on-error font-semibold cursor-pointer disabled:opacity-50"
                >
                  Confirm delete
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={busy}
                  className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmingDelete(true)}
                disabled={busy}
                className="px-4 py-2 rounded-lg bg-error/10 text-error font-semibold disabled:opacity-50 cursor-pointer"
              >
                Delete
              </button>
            )
          ) : <span />}
          <div className="flex gap-2">
            {!confirmingDelete && (
              <>
                <button
                  type="button"
                  onClick={onClose}
                  disabled={busy}
                  className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold disabled:opacity-50 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={saveDisabled}
                  className="px-4 py-2 rounded-lg bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-semibold disabled:opacity-60 cursor-pointer"
                >
                  {busy ? 'Saving…' : 'Save'}
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default CustomPresetEditorModal
