import { useMemo, useState } from 'react'
import {
  useActivateCustomProfile,
  useCreateCustomProfile,
  useDeleteCustomProfile,
  useRoutingPreferences,
  useUpdateCustomProfile,
  useUpdateRoutingPreferences,
} from '../hooks/useRoutingPreferences.js'
import CustomPresetEditorModal from './CustomPresetEditorModal.jsx'
import PresetDetailsModal from './PresetDetailsModal.jsx'

const MAX_CUSTOM_PROFILES = 5

const TRAVEL_MODE_LABELS = {
  WALKING:    { label: 'Walking',    icon: 'directions_walk' },
  WHEELCHAIR: { label: 'Wheelchair', icon: 'accessible' },
}

const PRESET_ICONS = {
  NONE:               'do_not_disturb_on',
  WHEELCHAIR_USER:    'accessible',
  BLIND_OR_LOW_VISION:'visibility',
  MOBILITY_LIMITED:   'elderly',
}

function PresetCard({
  active,
  title,
  ariaLabel,
  subtitle,
  travelMode,
  constraintCount,
  iconName,
  onClick,
  onInfoClick,
  infoAriaLabel,
  disabled,
}) {
  const tm = travelMode ? TRAVEL_MODE_LABELS[travelMode] : null

  function handleKeyDown(e) {
    if (disabled) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      onClick()
    }
  }

  // Card uses role="button" rather than <button> so the optional Edit action
  // inside it can be a real <button> without nesting interactive elements.
  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label={ariaLabel}
      aria-disabled={disabled || undefined}
      onClick={disabled ? undefined : onClick}
      onKeyDown={handleKeyDown}
      className={`relative text-left p-4 rounded-2xl border-2 transition-colors flex flex-col gap-2 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/40 ${
        active
          ? 'border-primary bg-primary/5'
          : 'border-outline-variant bg-surface-container hover:border-primary/40'
      } ${disabled ? 'opacity-60 cursor-wait' : ''}`}
    >
      {active && (
        <span className="absolute top-2 right-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary text-on-primary text-[11px] font-bold">
          <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>check</span>
          Active
        </span>
      )}
      <div className="flex items-center gap-2">
        <span className="material-symbols-outlined text-on-surface-variant">{iconName}</span>
        <span className="font-bold text-on-surface">{title}</span>
      </div>
      {subtitle && (
        <span className="text-xs text-on-surface-variant">{subtitle}</span>
      )}
      <div className="flex items-center gap-3 text-xs text-on-surface-variant mt-1">
        <span>{constraintCount} {constraintCount === 1 ? 'rule' : 'rules'}</span>
        {tm && (
          <span className="inline-flex items-center gap-1">
            <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>{tm.icon}</span>
            {tm.label}
          </span>
        )}
      </div>
      {onInfoClick && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onInfoClick() }}
          aria-label={infoAriaLabel}
          className="absolute bottom-2 right-2 rounded-full p-1 hover:bg-surface-container cursor-pointer"
        >
          <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>info</span>
        </button>
      )}
    </div>
  )
}

function RoutingPreferencesSection() {
  const { data, isPending, isError, error, refetch } = useRoutingPreferences()
  const updatePrefs = useUpdateRoutingPreferences()
  const createCustom = useCreateCustomProfile()
  const updateCustom = useUpdateCustomProfile()
  const deleteCustom = useDeleteCustomProfile()
  const activateCustom = useActivateCustomProfile()

  const [editorMode, setEditorMode] = useState(null) // 'create' | 'edit' | null
  const [editingProfile, setEditingProfile] = useState(null)
  const [editorError, setEditorError] = useState('')
  // detailsTarget shape: { kind: 'builtin', preset } | { kind: 'custom', profile } | null
  const [detailsTarget, setDetailsTarget] = useState(null)

  const presetByName = useMemo(() => {
    const map = new Map()
    for (const p of data?.availablePresets ?? []) map.set(p.name, p)
    return map
  }, [data?.availablePresets])

  const visiblePresets = useMemo(() => {
    // Hide CUSTOM from the catalog — it's not directly selectable; activating
    // a custom profile is the only path to that state.
    return (data?.availablePresets ?? []).filter(p => p.name !== 'CUSTOM')
  }, [data?.availablePresets])

  if (isPending) {
    return (
      <section className="bg-surface-container-low rounded-2xl shadow-sm p-6">
        <p className="text-sm text-on-surface-variant">Loading routing preferences…</p>
      </section>
    )
  }

  if (isError) {
    return (
      <section className="bg-surface-container-low rounded-2xl shadow-sm p-6 flex flex-col gap-2">
        <p role="alert" className="text-sm text-error">
          Failed to load routing preferences{error?.message ? `: ${error.message}` : '.'}
        </p>
        <button
          type="button"
          onClick={() => refetch()}
          className="self-start px-3 py-1.5 rounded-lg bg-surface-container text-on-surface font-semibold text-sm cursor-pointer"
        >
          Retry
        </button>
      </section>
    )
  }

  const {
    preferredPreset,
    activeCustomProfileId,
    customProfiles = [],
  } = data

  const atCap = customProfiles.length >= MAX_CUSTOM_PROFILES

  function selectBuiltIn(presetName) {
    if (preferredPreset === presetName && !activeCustomProfileId) return
    updatePrefs.mutate({ preferredPreset: presetName })
  }

  function activateCustomById(id) {
    if (activeCustomProfileId === id) return
    activateCustom.mutate(id)
  }

  function openCreate() {
    setEditingProfile(null)
    setEditorMode('create')
    setEditorError('')
  }

  function openEdit(profile) {
    setDetailsTarget(null)
    setEditingProfile(profile)
    setEditorMode('edit')
    setEditorError('')
  }

  function closeEditor() {
    setEditorMode(null)
    setEditingProfile(null)
    setEditorError('')
  }

  function openBuiltinDetails(preset) {
    setDetailsTarget({ kind: 'builtin', preset })
  }

  function openCustomDetails(profile) {
    setDetailsTarget({ kind: 'custom', profile })
  }

  function closeDetails() {
    setDetailsTarget(null)
  }

  async function handleSaveEditor(form) {
    setEditorError('')
    try {
      if (editorMode === 'create') {
        await createCustom.mutateAsync(form)
      } else if (editorMode === 'edit' && editingProfile) {
        await updateCustom.mutateAsync({ id: editingProfile.id, body: form })
      }
      closeEditor()
    } catch (e) {
      setEditorError(e.message || 'Failed to save preset.')
      throw e
    }
  }

  async function handleDeleteEditor() {
    if (!editingProfile) return
    setEditorError('')
    try {
      await deleteCustom.mutateAsync(editingProfile.id)
      closeEditor()
    } catch (e) {
      setEditorError(e.message || 'Failed to delete preset.')
      throw e
    }
  }

  async function handleDeleteFromDetails() {
    const profile = detailsTarget?.kind === 'custom' ? detailsTarget.profile : null
    if (!profile) return
    // The modal already collected an inline confirmation before calling us.
    try {
      await deleteCustom.mutateAsync(profile.id)
      setDetailsTarget(null)
    } catch (e) {
      // Re-throw so the modal can keep itself open if the backend rejects;
      // we intentionally avoid window.alert to stay inside our UI surface.
      console.error('Failed to delete preset', e)
    }
  }

  const editorBusy =
    createCustom.isPending || updateCustom.isPending || deleteCustom.isPending

  return (
    <section className="bg-surface-container-low rounded-2xl shadow-sm p-6 flex flex-col gap-4">
      <div className="flex items-baseline justify-between gap-2 flex-wrap">
        <h2 className="text-xl font-bold font-headline text-on-surface">Routing preferences</h2>
        <span className="text-xs text-on-surface-variant">
          {customProfiles.length}/{MAX_CUSTOM_PROFILES} custom presets
        </span>
      </div>
      <p className="text-sm text-on-surface-variant -mt-2">
        Pick a preset to shape every accessible-route request you make. Built-in presets cover
        common needs; you can also save up to {MAX_CUSTOM_PROFILES} of your own.
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {visiblePresets.map(preset => {
          const isActive = !activeCustomProfileId && preferredPreset === preset.name
          return (
            <PresetCard
              key={preset.name}
              active={isActive}
              title={preset.label}
              ariaLabel={`Activate ${preset.label}`}
              travelMode={preset.defaultTravelMode}
              constraintCount={preset.constraints?.length ?? 0}
              iconName={PRESET_ICONS[preset.name] ?? 'tune'}
              disabled={updatePrefs.isPending || activateCustom.isPending}
              onClick={() => selectBuiltIn(preset.name)}
              onInfoClick={() => openBuiltinDetails(preset)}
              infoAriaLabel={`View ${preset.label} details`}
            />
          )
        })}

        {customProfiles.map(profile => {
          const isActive = activeCustomProfileId === profile.id
          return (
            <PresetCard
              key={`custom-${profile.id}`}
              active={isActive}
              title={profile.name}
              ariaLabel={`Activate ${profile.name}`}
              subtitle="Custom"
              travelMode={profile.preferredTravelMode}
              constraintCount={profile.constraints?.length ?? 0}
              iconName="bookmark"
              disabled={updatePrefs.isPending || activateCustom.isPending}
              onClick={() => activateCustomById(profile.id)}
              onInfoClick={() => openCustomDetails(profile)}
              infoAriaLabel={`View ${profile.name} details`}
            />
          )
        })}

        <button
          type="button"
          onClick={openCreate}
          disabled={atCap}
          title={atCap ? `You can save at most ${MAX_CUSTOM_PROFILES} custom presets.` : ''}
          className="p-4 rounded-2xl border-2 border-dashed border-outline-variant text-on-surface-variant flex flex-col items-center justify-center gap-1 hover:border-primary/40 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer min-h-[100px]"
        >
          <span className="material-symbols-outlined">add</span>
          <span className="text-sm font-semibold">Create custom preset</span>
          {atCap && <span className="text-xs">Limit reached</span>}
        </button>
      </div>

      {editorMode && (
        <CustomPresetEditorModal
          mode={editorMode}
          profile={editingProfile}
          availableConstraints={data.availableConstraints ?? []}
          onClose={closeEditor}
          onSave={handleSaveEditor}
          onDelete={handleDeleteEditor}
          busy={editorBusy}
          errorMessage={editorError}
        />
      )}

      {detailsTarget?.kind === 'builtin' && (
        <PresetDetailsModal
          title={detailsTarget.preset.label}
          subtitle="Built-in preset"
          selectedConstraints={detailsTarget.preset.constraints ?? []}
          allConstraints={data.availableConstraints ?? []}
          travelMode={detailsTarget.preset.defaultTravelMode}
          onClose={closeDetails}
        />
      )}
      {detailsTarget?.kind === 'custom' && (
        <PresetDetailsModal
          title={detailsTarget.profile.name}
          subtitle="Custom preset"
          selectedConstraints={detailsTarget.profile.constraints ?? []}
          allConstraints={data.availableConstraints ?? []}
          travelMode={detailsTarget.profile.preferredTravelMode}
          onClose={closeDetails}
          onEdit={() => openEdit(detailsTarget.profile)}
          onDelete={handleDeleteFromDetails}
        />
      )}
    </section>
  )
}

export default RoutingPreferencesSection
