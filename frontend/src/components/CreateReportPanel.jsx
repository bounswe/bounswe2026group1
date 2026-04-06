import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { apiFetch } from '../services/api.js'
import { mapReport } from '../services/reportService.js'
import { REPORT_TAGS } from '../utils/reportTagConfig.js'

const TAGS = Object.entries(REPORT_TAGS).map(([value, cfg]) => ({ value, ...cfg }))

function CreateReportPanel({ position, onClose, onCreated }) {
  const { token, userId } = useAuth()
  const navigate = useNavigate()
  const [tag, setTag] = useState('')
  const [description, setDescription] = useState('')
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef(null)

  function handleImageChange(e) {
    const file = e.target.files[0]
    if (!file) return
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  function handleDrop(e) {
    e.preventDefault()
    const file = e.dataTransfer.files[0]
    if (!file) return
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  async function handleSubmit() {
    if (!token) { onClose(); navigate('/login'); return }
    if (!position) return setError('Click on the map to set a location.')
    if (!tag) return setError('Please select a category.')
    if (!description.trim()) return setError('Please provide a description.')

    setSubmitting(true)
    setError('')
    try {
      const created = await apiFetch('/api/reports', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          userId,
          latitude: position.lat,
          longitude: position.lng,
          description: description.trim(),
          tag,
        }),
      })

      let imageUrl = null
      if (imageFile) {
        const formData = new FormData()
        formData.append('file', imageFile)
        const mediaRes = await fetch(`${import.meta.env.VITE_API_URL}/api/reports/${created.reportId}/media`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          body: formData,
        })
        if (mediaRes.ok) {
          const mediaData = await mediaRes.json()
          imageUrl = mediaData.mediaUrl
        }
      }

      const mapped = mapReport(created)
      if (imageUrl) mapped.image = imageUrl
      onCreated(mapped)
      onClose()
    } catch (err) {
      setError('Failed to submit report. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <aside className="fixed top-0 right-0 h-full z-[1200] w-full lg:w-[480px] bg-white overflow-y-auto border-l border-outline-variant/10 flex flex-col">

      {/* Header */}
      <div className="px-8 pt-8 pb-4 flex items-start justify-between flex-shrink-0">
        <div>
          <h2 className="text-2xl font-extrabold font-headline text-on-surface">New Report</h2>
          <p className="text-sm text-on-surface-variant mt-1">
            {position
              ? `📍 ${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}`
              : 'Click on the map to set location'}
          </p>
        </div>
        <button
          onClick={onClose}
          className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center hover:bg-surface-container-high transition-colors"
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
            className="w-full h-44 rounded-2xl border-2 border-dashed border-outline-variant/40 bg-surface-container flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-primary/50 hover:bg-primary/5 transition-colors overflow-hidden"
          >
            {imagePreview ? (
              <img src={imagePreview} alt="Preview" className="w-full h-full object-cover" />
            ) : (
              <>
                <span className="material-symbols-outlined text-4xl text-on-surface-variant">add_a_photo</span>
                <p className="text-sm font-medium text-on-surface-variant">Upload or drag photos here</p>
                <p className="text-xs text-outline">Maximum file size: 10MB (JPG, PNG)</p>
              </>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            className="hidden"
            onChange={handleImageChange}
          />
        </div>

        {/* Category */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Category</p>
          <div className="grid grid-cols-3 gap-2">
            {TAGS.map(t => (
              <button
                key={t.value}
                onClick={() => setTag(t.value)}
                className="flex flex-col items-center gap-1.5 p-3 rounded-xl border-2 transition-all text-xs font-semibold border-outline-variant/20 bg-surface-container text-on-surface-variant hover:border-primary/40"
                style={tag === t.value ? { borderColor: t.color, backgroundColor: t.color + '1a', color: t.color } : {}}
              >
                <span className="material-symbols-outlined text-xl">{t.icon}</span>
                {t.label}
              </button>
            ))}
          </div>
        </div>

        {/* Details */}
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">Details</p>
          <textarea
            value={description}
            onChange={e => setDescription(e.target.value)}
            rows={4}
            placeholder="Provide a brief description of the issue..."
            className="w-full rounded-xl border border-outline-variant/30 bg-surface-container p-4 text-sm text-on-surface placeholder-on-surface-variant/50 resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
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
            {submitting ? 'Submitting...' : 'Submit Report'}
          </button>
        </div>

      </div>
    </aside>
  )
}

export default CreateReportPanel
