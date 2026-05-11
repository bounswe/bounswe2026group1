import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { submitFixRequest } from '../services/reportService.js'

/**
 * CreateFixRequestPanel
 *
 * Modal sheet layered over the report panel. Two inputs only:
 *   - photo (required, JPEG/PNG)
 *   - description (optional, ≤1000 chars)
 *
 * Location and tag are inherited from the parent report — the user already
 * picked them when the report was created, so we don't ask again.
 */
const MAX_FILES = 5

function CreateFixRequestPanel({ reportId, reportTitle, onClose, onSubmitted }) {
  const { token, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [imageFiles, setImageFiles] = useState([])
  const [imagePreviews, setImagePreviews] = useState([])
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef(null)

  // Revoke ALL object URLs only when the component unmounts (not on every
  // imagePreviews change — revoking on every update would immediately
  // invalidate thumbnails that were just added).
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => () => { imagePreviews.forEach(url => URL.revokeObjectURL(url)) }, [])

  function addFiles(files) {
    const newFiles = Array.from(files).filter(f => ['image/jpeg', 'image/png'].includes(f.type))
    if (newFiles.length === 0) {
      setError('Only JPG or PNG files are accepted.')
      return
    }
    const remaining = MAX_FILES - imageFiles.length
    if (remaining <= 0) {
      setError(`You can attach up to ${MAX_FILES} photos.`)
      return
    }
    const toAdd = newFiles.slice(0, remaining)
    if (toAdd.length < newFiles.length) {
      setError(`You can attach up to ${MAX_FILES} photos. Only the first ${toAdd.length} were added.`)
    } else {
      setError('')
    }
    setImageFiles(prev => [...prev, ...toAdd])
    setImagePreviews(prev => [...prev, ...toAdd.map(f => URL.createObjectURL(f))])
  }

  function removeFile(index) {
    URL.revokeObjectURL(imagePreviews[index])
    setImageFiles(prev => prev.filter((_, i) => i !== index))
    setImagePreviews(prev => prev.filter((_, i) => i !== index))
  }

  function handleImageChange(e) {
    const files = e.target.files
    if (!files || files.length === 0) return
    addFiles(files)
    // Reset input so the same file can be selected again if removed
    e.target.value = ''
  }

  function handleDrop(e) {
    e.preventDefault()
    const files = e.dataTransfer.files
    if (!files || files.length === 0) return
    addFiles(files)
  }

  async function handleSubmit() {
    if (!isAuthenticated) { onClose(); navigate('/login'); return }
    if (imageFiles.length === 0) return setError('Please attach at least one photo of the fixed area.')

    setSubmitting(true)
    setError('')
    try {
      const created = await submitFixRequest(reportId, imageFiles, description, token)
      onSubmitted?.(created)
      onClose()
    } catch (err) {
      // The backend returns 409 for a duplicate OPEN fix request. Surface
      // a friendlier message — server returns the raw status reason which is
      // OK but not great copy.
      if (err.status === 409) {
        setError('Someone else already submitted a fix report for this. Vote on theirs instead.')
      } else if (err.status === 400) {
        setError(err.message || 'Invalid submission. Check your photo and description length.')
      } else {
        setError(err.message || 'Failed to submit fix report. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div
        className="fixed inset-0 bg-black/40 z-[1300]"
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        role="dialog"
        aria-label="Submit fix report"
        className="fixed top-0 right-0 h-full z-[1400] w-full lg:w-[500px] bg-surface-container-low overflow-y-auto border-l border-outline-variant/10 flex flex-col"
      >
        {/* Header */}
        <div className="px-8 pt-8 pb-4 flex items-start justify-between flex-shrink-0">
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-full bg-tertiary-container flex items-center justify-center flex-shrink-0">
              <span className="material-symbols-outlined text-on-tertiary-container">build</span>
            </div>
            <div>
              <h2 className="text-2xl font-extrabold font-headline text-on-surface leading-tight">
                Report as Fixed
              </h2>
              <p className="text-sm text-on-surface-variant mt-1 line-clamp-2">
                {reportTitle ? `For: ${reportTitle}` : 'Share a photo so the community can verify.'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center hover:bg-surface-container-high transition-colors flex-shrink-0"
            aria-label="Close"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        <div className="px-8 pb-10 flex flex-col gap-6">
          {/* Inherited-context banner */}
          <div className="bg-surface-container px-4 py-3 rounded-xl flex items-center gap-3 text-xs text-on-surface-variant">
            <span className="material-symbols-outlined text-base">link</span>
            <span>Location and category are inherited from the parent report.</span>
          </div>

          {/* Photos (at least one required) */}
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              Photos of the fixed area <span className="text-error">*</span>
            </p>

            {/* Thumbnails of selected files */}
            {imagePreviews.length > 0 && (
              <div className="flex flex-wrap gap-2 mb-3">
                {imagePreviews.map((src, i) => (
                  <div key={i} className="relative w-20 h-20 rounded-xl overflow-hidden border border-outline-variant/30">
                    <img src={src} alt={`Preview ${i + 1}`} className="w-full h-full object-cover" />
                    <button
                      type="button"
                      onClick={() => removeFile(i)}
                      className="absolute top-0.5 right-0.5 w-5 h-5 rounded-full bg-error text-on-error flex items-center justify-center text-xs hover:opacity-90"
                      aria-label={`Remove photo ${i + 1}`}
                    >
                      <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>close</span>
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div
              onClick={() => fileInputRef.current?.click()}
              onDrop={handleDrop}
              onDragOver={e => e.preventDefault()}
              className="w-full h-32 rounded-2xl border-2 border-dashed border-outline-variant/40 bg-surface-container flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-primary/50 hover:bg-primary/5 transition-colors"
            >
              <span className="material-symbols-outlined text-4xl text-on-surface-variant">add_a_photo</span>
              <p className="text-sm font-medium text-on-surface-variant">
                {imageFiles.length >= MAX_FILES
                  ? `${MAX_FILES}/${MAX_FILES} photos — limit reached`
                  : imagePreviews.length === 0
                    ? 'Upload or drop photos of the resolved spot'
                    : `Add more photos (${imageFiles.length}/${MAX_FILES})`}
              </p>
              <p className="text-xs text-outline">JPG or PNG, max 15 MB each</p>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png"
              multiple
              disabled={imageFiles.length >= MAX_FILES}
              className="hidden"
              onChange={handleImageChange}
            />
          </div>

          {/* Description (optional) */}
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              What changed? <span className="text-on-surface-variant/70 normal-case tracking-normal font-medium">(optional)</span>
            </p>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value.slice(0, 1000))}
              rows={3}
              maxLength={1000}
              placeholder="A new ramp was installed last week."
              className="w-full rounded-xl border border-outline-variant/30 bg-surface-container p-4 text-sm text-on-surface placeholder-on-surface-variant/50 resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            <p className={`text-xs text-right mt-1 ${description.length >= 900 ? 'text-error' : 'text-outline'}`}>
              {description.length}/1000
            </p>
          </div>

          {/* Error */}
          {error && (
            <p role="alert" className="text-sm text-error bg-error-container/20 rounded-lg px-4 py-2">
              {error}
            </p>
          )}

          {/* Helper copy explaining the rule */}
          <p className="text-xs text-on-surface-variant text-center">
            Other users will vote on your fix. It confirms as <strong>Fixed</strong> when at least
            5 people agree <strong>and</strong> consensus reaches 60% — then it auto-deletes after 7 days.
          </p>

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
              {submitting ? 'Submitting…' : 'Submit Fix Report'}
            </button>
          </div>
        </div>
      </aside>
    </>
  )
}

export default CreateFixRequestPanel
