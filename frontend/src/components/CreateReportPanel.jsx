import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext.jsx'
import { createReport, mapReport } from '../services/reportService.js'
import { currentUserKey } from '../hooks/useCurrentUser.js'
import { OBJECT_TYPES } from '../utils/objectTypeConfig.js'
import ObjectTutorialModal from './ObjectTutorialModal.jsx'

// Mirrors backend `S3MediaService.ALLOWED_CONTENT_TYPES`. Apple devices
// produce MOV (video/quicktime); most other devices produce MP4.
const ALLOWED_MEDIA_MIME = ['image/jpeg', 'image/jpg', 'image/png', 'video/mp4', 'video/quicktime']
const MAX_MEDIA_BYTES = 15 * 1024 * 1024

function CreateReportPanel({ position, positionLabel, onClose, onCreated, onError }) {
  const { token, userId } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [reportType, setReportType] = useState('OBSTACLE')
  const [environment, setEnvironment] = useState('OUTDOOR')
  const [objects, setObjects] = useState([])
  const [description, setDescription] = useState('')
  const [imageFiles, setImageFiles] = useState([])
  const [imagePreviews, setImagePreviews] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  // Errors raised while picking a file (unsupported MIME, oversized).
  // Kept separate from the submit-level `error` so they render right
  // under the dropzone instead of at the bottom of the form where users
  // would have to scroll to see them.
  const [mediaError, setMediaError] = useState('')
  const fileInputRef = useRef(null)
  // Object type whose tutorial modal is currently shown, null when closed.
  const [tutorialFor, setTutorialFor] = useState(null)

  // Bottom-sheet drag-to-resize (mobile only — desktop keeps right-sidebar layout).
  // Snap points in dvh; below DISMISS_THRESHOLD on release we call onClose().
  const SHEET_SNAP_POINTS = [25, 60, 90]
  const SHEET_DISMISS_THRESHOLD = 15
  const SHEET_DEFAULT_DVH = 60
  const [sheetHeightDvh, setSheetHeightDvh] = useState(SHEET_DEFAULT_DVH)
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef({ startY: 0, startHeight: SHEET_DEFAULT_DVH, active: false })

  const [isMobileSheet, setIsMobileSheet] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(max-width: 1023px)').matches
      : true
  )
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const mql = window.matchMedia('(max-width: 1023px)')
    function onChange() { setIsMobileSheet(mql.matches) }
    mql.addEventListener?.('change', onChange)
    return () => mql.removeEventListener?.('change', onChange)
  }, [])

  function handleHandlePointerDown(e) {
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = { startY: e.clientY, startHeight: sheetHeightDvh, active: true }
    setIsDragging(true)
  }
  function handleHandlePointerMove(e) {
    if (!dragRef.current.active) return
    const deltaY = e.clientY - dragRef.current.startY
    const dvhDelta = (deltaY / window.innerHeight) * 100
    const next = dragRef.current.startHeight - dvhDelta
    setSheetHeightDvh(Math.max(5, Math.min(95, next)))
  }
  function handleHandlePointerUp(e) {
    if (!dragRef.current.active) return
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch { /* noop */ }
    dragRef.current.active = false
    setIsDragging(false)
    setSheetHeightDvh(prev => {
      if (prev < SHEET_DISMISS_THRESHOLD) {
        onClose()
        return SHEET_DEFAULT_DVH
      }
      return SHEET_SNAP_POINTS.reduce(
        (best, p) => (Math.abs(p - prev) < Math.abs(best - prev) ? p : best),
        SHEET_SNAP_POINTS[0],
      )
    })
  }

  // Recomputed every render — drives duplicate-type prevention
  const selectedTypes = new Set(objects.map(o => o.objectType).filter(Boolean))

  // Which OBJECT_TYPES are visible given the current reportType + environment
  const visibleTypes = (reportType === 'FEATURE'
    ? OBJECT_TYPES.filter(t => t.type === 'RAMP')
    : OBJECT_TYPES
  ).filter(t => t.environments.includes(environment))

  // ── media (image or video) ─────────────────────────────────────────────────
  // Allowlist mirrors backend S3MediaService.ALLOWED_CONTENT_TYPES so the
  // client rejects unsupported types before incurring an upload round-trip.
  // 15 MB matches both backend behaviour and the avatar uploader.

  function handleImageChange(e) {
    const files = Array.from(e.target.files)
    if (!files.length) return
    addFiles(files)
    e.target.value = ''
  }

  function handleDrop(e) {
    e.preventDefault()
    const files = Array.from(e.dataTransfer.files)
    if (!files.length) return
    addFiles(files)
  }

  function addFiles(newFiles) {
    // Validate MIME + size against the backend allowlist before adding. Reject
    // the whole batch on the first bad file so the user fixes it explicitly
    // rather than getting a partial success.
    for (const f of newFiles) {
      if (!ALLOWED_MEDIA_MIME.includes(f.type)) {
        setMediaError('Unsupported file type. Use JPEG, PNG, MP4, or MOV.')
        return
      }
      if (f.size > MAX_MEDIA_BYTES) {
        setMediaError('File must be 15 MB or smaller.')
        return
      }
    }
    setMediaError('')
    setImageFiles(prev => {
      const combined = [...prev, ...newFiles].slice(0, 5) // Max 5 files
      setImagePreviews(combined.map(f => URL.createObjectURL(f)))
      return combined
    })
  }

  function removeImage(index) {
    setImageFiles(prev => {
      const next = [...prev]
      next.splice(index, 1)
      setImagePreviews(next.map(f => URL.createObjectURL(f)))
      return next
    })
  }

  // ── report type toggle ─────────────────────────────────────────────────────

  function handleReportTypeChange(newType) {
    setReportType(newType)
    if (newType === 'FEATURE') {
      // Clear objectType on any non-RAMP card; RAMP cards lose their issues
      setObjects(prev => prev.map(o =>
        o.objectType && o.objectType !== 'RAMP'
          ? { ...o, objectType: null, issues: [], measurements: {} }
          : { ...o, issues: [] }
      ))
    }
  }

  function handleEnvironmentChange(newEnv) {
    setEnvironment(newEnv)
    // Clear objectType + issues + measurements on any card whose ObjectType
    // does not support the new environment (e.g. SIDEWALK is OUTDOOR-only,
    // WASHROOM is INDOOR-only).
    setObjects(prev => prev.map(o => {
      if (!o.objectType) return o
      const cfg = OBJECT_TYPES.find(t => t.type === o.objectType)
      if (cfg && cfg.environments.includes(newEnv)) return o
      return { ...o, objectType: null, issues: [], measurements: {} }
    }))
  }

  // ── object management ──────────────────────────────────────────────────────

  function addObject() {
    setObjects(prev => [
      ...prev,
      { id: crypto.randomUUID(), objectType: null, issues: [], measurements: {}, expanded: true, showMeasurements: false },
    ])
  }

  function removeObject(id) {
    setObjects(prev => prev.filter(o => o.id !== id))
  }

  function toggleExpanded(id) {
    setObjects(prev => prev.map(o => o.id === id ? { ...o, expanded: !o.expanded } : o))
  }

  function selectObjectType(id, type) {
    setObjects(prev => prev.map(o =>
      o.id === id ? { ...o, objectType: type, issues: [], measurements: {} } : o
    ))
  }

  function toggleIssue(id, issueKey) {
    setObjects(prev => prev.map(o => {
      if (o.id !== id) return o
      const has = o.issues.includes(issueKey)
      return { ...o, issues: has ? o.issues.filter(k => k !== issueKey) : [...o.issues, issueKey] }
    }))
  }

  function setMeasurement(id, key, value) {
    setObjects(prev => prev.map(o =>
      o.id === id ? { ...o, measurements: { ...o.measurements, [key]: value } } : o
    ))
  }

  function toggleShowMeasurements(id) {
    setObjects(prev => prev.map(o =>
      o.id === id ? { ...o, showMeasurements: !o.showMeasurements } : o
    ))
  }

  // ── submit ─────────────────────────────────────────────────────────────────

  async function handleSubmit() {
    if (!token) { onClose(); navigate('/login'); return }
    if (!position) return setError('Click on the map to set a location.')
    if (objects.length === 0) return setError('Add at least one object to describe the issue.')

    for (const obj of objects) {
      if (!obj.objectType) return setError('Select a type for every object card.')
      if (reportType === 'OBSTACLE' && obj.issues.length === 0) {
        const label = OBJECT_TYPES.find(t => t.type === obj.objectType)?.label ?? obj.objectType
        return setError(`Select at least one issue for the "${label}" object.`)
      }
    }

    if (!description.trim()) return setError('Please provide a description.')

    setSubmitting(true)
    setError('')
    try {
      const body = {
        userId,
        latitude: position.lat,
        longitude: position.lng,
        description: description.trim(),
        reportType,
        environment,
        objects: objects.map(o => ({
          objectType: o.objectType,
          issues: o.issues,
          measurements: JSON.stringify(
            Object.fromEntries(Object.entries(o.measurements).filter(([, v]) => v !== ''))
          ),
        })),
        token,
      }
      const created = await createReport(body)

      let uploadedMedia = []
      if (imageFiles.length > 0) {
        const formData = new FormData()
        imageFiles.forEach(file => formData.append('file', file))
        const mediaRes = await fetch(`${import.meta.env.VITE_API_URL}/api/reports/${created.reportId}/media`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        })
        if (mediaRes.ok) {
          // Get the json directly, do not map it
          uploadedMedia = await mediaRes.json()
        }
      }

      const mapped = mapReport(created)
      if (uploadedMedia.length > 0) {
        mapped.image = uploadedMedia[0].url // Use the first photo URL for the map preview
        mapped.media = uploadedMedia
      }
      // A successful submission awards the +10 report-submit bonus. Invalidate
      // leaderboard + profile caches so the caller's own tab reflects the
      // new balance immediately; the SSE event covers other tabs/users.
      queryClient.invalidateQueries({ queryKey: ['leaderboard'] })
      queryClient.invalidateQueries({ queryKey: currentUserKey })
      onCreated(mapped)
      onClose()
    } catch (err) {
      const message = err.message || 'Failed to submit report. Please try again.'
      setError(message)
      // Surface the same message as a global toast for callers that wired one
      // up (Home does). The inline error stays for persistent context.
      if (onError) onError(message)
    } finally {
      setSubmitting(false)
    }
  }

  // ── render ─────────────────────────────────────────────────────────────────

  return (
    <div className="fixed inset-0 z-[1200] pointer-events-none flex" style={{ flexDirection: isMobileSheet ? 'column' : 'row', justifyContent: 'flex-end' }}>
      <aside
        style={isMobileSheet
          ? {
              height: `${sheetHeightDvh}dvh`,
              maxHeight: `${sheetHeightDvh}dvh`,
              width: '100%',
              borderTopLeftRadius: '32px',
              borderTopRightRadius: '32px',
              borderTop: '1px solid rgba(172,173,173,.2)',
              boxShadow: '0 -10px 40px rgba(0,0,0,0.2)',
            }
          : {
              width: '500px',
              height: '100%',
              maxHeight: '100%',
              borderLeft: '1px solid rgba(172,173,173,.1)',
            }}
        className={`pointer-events-auto bg-surface-container-low flex flex-col relative overflow-y-auto ${isDragging ? '' : 'transition-[height,max-height] duration-200 ease-out'}`}
      >

        {/* Mobile drag handle — pill is decorative, the wider hit-area carries the pointer events. */}
        {isMobileSheet && (
          <div
            role="slider"
            aria-label="Resize report panel"
            aria-valuemin={SHEET_SNAP_POINTS[0]}
            aria-valuemax={SHEET_SNAP_POINTS[SHEET_SNAP_POINTS.length - 1]}
            aria-valuenow={Math.round(sheetHeightDvh)}
            tabIndex={-1}
            onPointerDown={handleHandlePointerDown}
            onPointerMove={handleHandlePointerMove}
            onPointerUp={handleHandlePointerUp}
            onPointerCancel={handleHandlePointerUp}
            className="flex-shrink-0 pt-3 pb-2 cursor-grab active:cursor-grabbing touch-none select-none"
          >
            <div className="w-12 h-1.5 bg-outline-variant/40 rounded-full mx-auto" />
          </div>
        )}

      {/* Header */}
      <div className="px-8 pt-2 lg:pt-8 pb-4 flex items-start justify-between flex-shrink-0">
        <div className="min-w-0">
          <h2 className="text-2xl font-extrabold font-headline text-on-surface">New Report</h2>
          {position ? (
            // Place name from reverse-geocoding sits on the primary line; raw
            // coordinates fall to a smaller secondary line so the user always
            // has the precise location available even when Nominatim resolves.
            <>
              <p className="text-sm font-semibold text-on-surface mt-1 truncate" title={positionLabel || undefined}>
                📍 {positionLabel || `${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}`}
              </p>
              {positionLabel && (
                <p className="text-[11px] text-on-surface-variant mt-0.5">
                  {position.lat.toFixed(5)}, {position.lng.toFixed(5)}
                </p>
              )}
            </>
          ) : (
            <p className="text-sm text-on-surface-variant mt-1">Click on the map to set location</p>
          )}
        </div>
        <button
          onClick={onClose}
          className="w-9 h-9 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors"
          aria-label="Close"
        >
          <span className="material-symbols-outlined text-base">close</span>
        </button>
      </div>

      <div className="px-8 pb-10 flex flex-col gap-6">

        {/* Visual Evidence */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Visual Evidence</p>
          <div
            onClick={() => fileInputRef.current?.click()}
            onDrop={handleDrop}
            onDragOver={e => e.preventDefault()}
            className="w-full min-h-[11rem] p-4 rounded-2xl border-2 border-dashed border-outline-variant/40 bg-surface-container flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-primary/50 hover:bg-primary/5 transition-colors overflow-hidden"
          >
            {imagePreviews.length > 0 ? (
              <div className="grid grid-cols-2 gap-2 w-full">
                {imagePreviews.map((preview, idx) => {
                  const isVideo = imageFiles[idx]?.type?.startsWith('video/')
                  return (
                    <div key={idx} className="relative aspect-video rounded-lg overflow-hidden group border border-outline-variant/20 bg-black/5">
                      {isVideo ? (
                        <video src={preview} className="w-full h-full object-cover" muted playsInline />
                      ) : (
                        <img src={preview} alt="Preview" className="w-full h-full object-cover" />
                      )}
                      {isVideo && (
                        <span className="absolute top-1 left-1 flex items-center gap-1 bg-black/60 text-white text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded-full">
                          <span className="material-symbols-outlined" style={{ fontSize: '12px' }}>movie</span>
                          Video
                        </span>
                      )}
                      <button
                        onClick={(e) => { e.stopPropagation(); removeImage(idx); }}
                        className="absolute top-1 right-1 w-6 h-6 bg-black/60 rounded-full flex items-center justify-center text-white opacity-0 group-hover:opacity-100 transition-opacity"
                        aria-label="Remove image"
                      >
                        <span className="material-symbols-outlined text-sm">close</span>
                      </button>
                    </div>
                  )
                })}
                {imagePreviews.length < 5 && (
                  <div className="aspect-video rounded-lg border-2 border-dashed border-outline-variant/40 flex flex-col items-center justify-center text-on-surface-variant hover:text-primary hover:border-primary/40 hover:bg-primary/5 transition-colors">
                    <span className="material-symbols-outlined text-2xl mb-1">add</span>
                    <span className="text-[10px] font-medium">Add more</span>
                  </div>
                )}
              </div>
            ) : (
              <>
                <span className="material-symbols-outlined text-4xl text-on-surface-variant">add_a_photo</span>
                <p className="text-sm font-medium text-on-surface-variant">Upload or drag photos / videos here</p>
                <p className="text-xs text-outline">Up to 5 files, 15 MB each (JPEG, PNG, MP4, MOV).</p>
              </>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept={ALLOWED_MEDIA_MIME.join(',')}
            className="hidden"
            onChange={handleImageChange}
          />
          {mediaError && (
            <p role="alert" className="mt-2 text-sm text-error bg-error-container/20 rounded-lg px-3 py-2">
              {mediaError}
            </p>
          )}
        </div>

        {/* Report Type */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Report Type</p>
          <div className="grid grid-cols-2 gap-2">
            {[
              { value: 'OBSTACLE', icon: 'construction',      label: 'Obstacle', desc: 'Something broken or missing' },
              { value: 'FEATURE',  icon: 'accessible_forward', label: 'Feature',  desc: 'Something helpful that exists' },
            ].map(t => (
              <button
                key={t.value}
                aria-label={t.label}
                onClick={() => handleReportTypeChange(t.value)}
                className="flex flex-col items-center gap-1.5 p-3 rounded-xl border-2 transition-all text-xs font-semibold"
                style={reportType === t.value
                  ? { borderColor: '#176a21', backgroundColor: 'rgba(23,106,33,.07)', color: '#176a21' }
                  : { borderColor: 'rgba(172,173,173,.25)', backgroundColor: '', color: '#5a5c5c' }}
              >
                <span className="material-symbols-outlined text-xl">{t.icon}</span>
                <span className="font-bold text-xs">{t.label}</span>
                <span className="text-[10px] opacity-70 text-center leading-tight">{t.desc}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Environment */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Environment</p>
          <div className="grid grid-cols-2 gap-2">
            {[
              { value: 'OUTDOOR', icon: 'wb_sunny', label: 'Outdoor' },
              { value: 'INDOOR',  icon: 'home',     label: 'Indoor' },
            ].map(e => (
              <button
                key={e.value}
                aria-label={e.label}
                onClick={() => handleEnvironmentChange(e.value)}
                className="flex items-center justify-center gap-2 py-2.5 rounded-xl border-2 text-xs font-semibold transition-all"
                style={environment === e.value
                  ? { borderColor: '#176a21', backgroundColor: 'rgba(23,106,33,.07)', color: '#176a21' }
                  : { borderColor: 'rgba(172,173,173,.25)', color: '#5a5c5c' }}
              >
                <span className="material-symbols-outlined text-base">{e.icon}</span>
                {e.label}
              </button>
            ))}
          </div>
        </div>

        {/* Objects */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
            Objects <span className="text-error text-[10px] font-normal normal-case tracking-normal">required</span>
          </p>

          <div className="flex flex-col gap-2 mb-2">
            {objects.map((obj, idx) => {
              const cfg = OBJECT_TYPES.find(t => t.type === obj.objectType) ?? null
              return (
                <div
                  key={obj.id}
                  className="rounded-2xl border-2 bg-surface-container overflow-hidden transition-colors"
                  style={{ borderColor: obj.objectType ? 'rgba(23,106,33,.2)' : 'rgba(172,173,173,.2)' }}
                >
                  {/* Card header */}
                  <div
                    className="flex items-center gap-2 px-4 py-3 cursor-pointer select-none"
                    onClick={() => toggleExpanded(obj.id)}
                  >
                    <div className="w-5 h-5 rounded-full bg-primary flex items-center justify-center flex-shrink-0">
                      <span className="text-on-primary text-[10px] font-bold">{idx + 1}</span>
                    </div>
                    <span className={`flex-1 text-sm font-semibold ${obj.objectType ? 'text-on-surface' : 'text-on-surface-variant italic'}`}>
                      {cfg ? cfg.label : 'Select a type…'}
                    </span>
                    <span className="material-symbols-outlined text-on-surface-variant text-base transition-transform" style={{ transform: obj.expanded ? 'rotate(180deg)' : '' }}>
                      expand_more
                    </span>
                    <button
                      onClick={e => { e.stopPropagation(); removeObject(obj.id) }}
                      className="w-7 h-7 rounded-full flex items-center justify-center text-on-surface-variant hover:text-error hover:bg-error/10 transition-colors"
                      aria-label="Remove object"
                    >
                      <span className="material-symbols-outlined text-base">delete</span>
                    </button>
                  </div>

                  {obj.expanded && (
                    <div className="px-4 pb-4 flex flex-col gap-4 border-t border-outline-variant/20 pt-3">

                      {/* Object type picker */}
                      <div>
                        <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">Object Type</p>
                        <div className="grid grid-cols-5 gap-1.5">
                          {visibleTypes.map(t => {
                            const isSelected = obj.objectType === t.type
                            const isDisabled = !isSelected && selectedTypes.has(t.type)
                            return (
                              <button
                                key={t.type}
                                aria-label={t.label}
                                disabled={isDisabled}
                                onClick={() => selectObjectType(obj.id, t.type)}
                                title={isDisabled ? `${t.label} already added` : t.label}
                                className="flex flex-col items-center gap-1 py-2 rounded-xl border-2 transition-all text-[10px] font-semibold disabled:opacity-35 disabled:cursor-not-allowed"
                                style={isSelected
                                  ? { borderColor: '#176a21', backgroundColor: 'rgba(23,106,33,.08)', color: '#176a21' }
                                  : { borderColor: 'rgba(172,173,173,.25)', backgroundColor: '#f0f1f1', color: '#5a5c5c' }}
                              >
                                <span className="material-symbols-outlined text-xl">{t.icon}</span>
                                {t.label}
                              </button>
                            )
                          })}
                        </div>
                      </div>

                      {/* Issues — only for OBSTACLE and when type is selected */}
                      {cfg && reportType === 'OBSTACLE' && (
                        <div>
                          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">
                            Issues <span className="text-error">*</span>
                          </p>
                          <div className="grid grid-cols-2 gap-1">
                            {cfg.issues.map(issue => (
                              <label
                                key={issue.key}
                                className="flex items-center gap-2 px-2.5 py-2 rounded-lg cursor-pointer hover:bg-primary/5 transition-colors"
                              >
                                <input
                                  type="checkbox"
                                  checked={obj.issues.includes(issue.key)}
                                  onChange={() => toggleIssue(obj.id, issue.key)}
                                  className="w-3.5 h-3.5 accent-primary"
                                />
                                <span className="text-xs font-medium text-on-surface">{issue.label}</span>
                              </label>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Measurements — optional, collapsible */}
                      {cfg && cfg.measurements.length > 0 && (
                        <div>
                          <div className="flex items-center justify-between gap-2 mb-2">
                            <button
                              onClick={() => toggleShowMeasurements(obj.id)}
                              className="flex items-center gap-1.5 text-xs font-semibold text-primary"
                            >
                              <span className="material-symbols-outlined text-base">straighten</span>
                              {obj.showMeasurements ? 'Hide measurements' : 'Show measurements (optional)'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setTutorialFor(obj.objectType)}
                              aria-label={`How to measure ${cfg.label}`}
                              className="flex items-center gap-1 text-xs font-semibold text-on-surface-variant hover:text-primary cursor-pointer"
                            >
                              <span className="material-symbols-outlined text-sm">help_outline</span>
                              How to measure
                            </button>
                          </div>
                          {obj.showMeasurements && (
                            <div className="flex flex-col gap-3">
                              {cfg.measurements.map(m => (
                                <div key={m.key}>
                                  <p className="text-[10px] font-semibold text-on-surface-variant mb-1">{m.label}</p>
                                  <div className="flex items-center rounded-lg border border-outline-variant/30 bg-surface-container-lowest overflow-hidden focus-within:border-primary/50 h-10">
                                    <input
                                      type="number"
                                      min="0"
                                      step="0.1"
                                      value={obj.measurements[m.key] ?? ''}
                                      onChange={e => setMeasurement(obj.id, m.key, e.target.value)}
                                      className="flex-1 min-w-0 px-3 text-sm bg-transparent outline-none text-on-surface [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                                      placeholder="—"
                                    />
                                    <span className="px-2.5 text-xs font-semibold text-on-surface-variant/70 border-l border-outline-variant/25 bg-surface-container self-stretch flex items-center shrink-0">
                                      {m.unit}
                                    </span>
                                    <div className="flex flex-col border-l border-outline-variant/25 shrink-0 self-stretch">
                                      <button
                                        type="button"
                                        tabIndex={-1}
                                        onClick={() => {
                                          const cur = parseFloat(obj.measurements[m.key]) || 0
                                          setMeasurement(obj.id, m.key, String(Math.round((cur + 0.1) * 10) / 10))
                                        }}
                                        className="flex-1 w-8 flex items-center justify-center text-on-surface-variant hover:text-primary hover:bg-surface-container transition-colors border-b border-outline-variant/25"
                                      >
                                        <span className="material-symbols-outlined" style={{ fontSize: 14 }}>expand_less</span>
                                      </button>
                                      <button
                                        type="button"
                                        tabIndex={-1}
                                        onClick={() => {
                                          const cur = parseFloat(obj.measurements[m.key]) || 0
                                          const next = Math.round((cur - 0.1) * 10) / 10
                                          setMeasurement(obj.id, m.key, String(next < 0 ? 0 : next))
                                        }}
                                        className="flex-1 w-8 flex items-center justify-center text-on-surface-variant hover:text-primary hover:bg-surface-container transition-colors"
                                      >
                                        <span className="material-symbols-outlined" style={{ fontSize: 14 }}>expand_more</span>
                                      </button>
                                    </div>
                                  </div>
                                  {m.accessible_min !== undefined && (
                                    <p className="text-[10px] text-primary mt-1 flex items-center gap-1">
                                      <span className="material-symbols-outlined text-xs">check_circle</span>
                                      ≥ {m.accessible_min} {m.unit} is accessible
                                    </p>
                                  )}
                                  {m.accessible_max !== undefined && (
                                    <p className="text-[10px] text-primary mt-1 flex items-center gap-1">
                                      <span className="material-symbols-outlined text-xs">check_circle</span>
                                      ≤ {m.accessible_max} {m.unit} is accessible
                                    </p>
                                  )}
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}

                    </div>
                  )}
                </div>
              )
            })}
          </div>

          <button
            onClick={addObject}
            className="w-full py-3 rounded-xl border-2 border-dashed border-primary/35 text-primary text-sm font-semibold flex items-center justify-center gap-1.5 hover:bg-primary/5 hover:border-primary/50 transition-colors"
          >
            <span className="material-symbols-outlined text-lg">add_circle</span>
            Add Object
          </button>
        </div>

        {/* Description */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Details</p>
          <textarea
            value={description}
            onChange={e => setDescription(e.target.value.slice(0, 1000))}
            rows={4}
            maxLength={1000}
            placeholder="Provide a brief description of the issue..."
            className="w-full rounded-xl border border-outline-variant/30 bg-surface-container p-4 text-sm text-on-surface placeholder-on-surface-variant/50 resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <p className={`text-xs text-right mt-1 ${description.length >= 900 ? 'text-error' : 'text-outline'}`}>
            {description.length}/1000
          </p>
        </div>

        {/* Error */}
        {error && (
          <p className="text-sm text-error bg-error-container/20 rounded-lg px-4 py-2">{error}</p>
        )}

        {/* Actions */}
        <div className="grid grid-cols-2 gap-3 pt-2">
          <button
            onClick={onClose}
            className="py-4 rounded-xl border border-outline-variant/30 text-on-surface font-semibold hover:bg-surface-container transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="py-4 rounded-xl bg-primary text-on-primary font-bold hover:opacity-90 active:scale-95 transition-all disabled:opacity-60"
          >
            {submitting ? 'Submitting…' : 'Submit Report'}
          </button>
        </div>

      </div>
      </aside>
      {tutorialFor && (
        <ObjectTutorialModal
          objectType={tutorialFor}
          onClose={() => setTutorialFor(null)}
        />
      )}
    </div>
  )
}

export default CreateReportPanel
