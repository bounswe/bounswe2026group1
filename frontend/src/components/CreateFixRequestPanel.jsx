import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
function CreateFixRequestPanel({ reportId, reportTitle, onClose, onSubmitted }) {
  const { t } = useTranslation()
  const { token, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef(null)

  useEffect(() => () => { if (imagePreview) URL.revokeObjectURL(imagePreview) }, [imagePreview])

  function setImage(file) {
    setImageFile(file)
    setImagePreview(URL.createObjectURL(file))
  }

  function handleImageChange(e) {
    const file = e.target.files?.[0]
    if (!file) return
    setImage(file)
  }

  function handleDrop(e) {
    e.preventDefault()
    const file = e.dataTransfer.files?.[0]
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError(t('report.createFixOnlyJpgPng'))
      return
    }
    setImage(file)
  }

  async function handleSubmit() {
    if (!isAuthenticated) { onClose(); navigate('/login'); return }
    if (!imageFile) return setError(t('report.createFixAttachPhoto'))

    setSubmitting(true)
    setError('')
    try {
      const created = await submitFixRequest(reportId, imageFile, description, token)
      onSubmitted?.(created)
      onClose()
    } catch (err) {
      // The backend returns 409 for a duplicate OPEN fix request. Surface
      // a friendlier message — server returns the raw status reason which is
      // OK but not great copy.
      if (err.status === 409) {
        setError(t('report.createFixDuplicate'))
      } else if (err.status === 400) {
        setError(err.message || t('report.createFixInvalidSubmission'))
      } else {
        setError(err.message || t('report.createFixGenericError'))
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
        aria-label={t('report.createFixAria')}
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
                {t('report.createFixHeading')}
              </h2>
              <p className="text-sm text-on-surface-variant mt-1 line-clamp-2">
                {reportTitle ? t('report.createFixFor', { title: reportTitle }) : t('report.createFixSubtitle')}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center hover:bg-surface-container-high transition-colors flex-shrink-0"
            aria-label={t('report.createFixClose')}
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        <div className="px-8 pb-10 flex flex-col gap-6">
          {/* Inherited-context banner */}
          <div className="bg-surface-container px-4 py-3 rounded-xl flex items-center gap-3 text-xs text-on-surface-variant">
            <span className="material-symbols-outlined text-base">link</span>
            <span>{t('report.createFixInheritedBanner')}</span>
          </div>

          {/* Photo (required) */}
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              {t('report.createFixPhotoLabel')} <span className="text-error">*</span>
            </p>
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
                  <p className="text-sm font-medium text-on-surface-variant">{t('report.createFixUploadHint')}</p>
                  <p className="text-xs text-outline">{t('report.createFixUploadFormat')}</p>
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

          {/* Description (optional) */}
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
              {t('report.createFixWhatChanged')} <span className="text-on-surface-variant/70 normal-case tracking-normal font-medium">{t('report.createFixOptional')}</span>
            </p>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value.slice(0, 1000))}
              rows={3}
              maxLength={1000}
              placeholder={t('report.createFixDescriptionPlaceholder')}
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
            {t('report.createFixRuleCopy')}
          </p>

          {/* Actions */}
          <div className="grid grid-cols-2 gap-3 pt-2">
            <button
              onClick={onClose}
              className="py-4 rounded-xl border border-outline-variant/30 text-on-surface font-semibold hover:bg-surface-container transition-colors"
            >
              {t('report.createFixCancel')}
            </button>
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="py-4 rounded-xl bg-primary text-on-primary font-bold hover:opacity-90 active:scale-95 transition-all disabled:opacity-60"
            >
              {submitting ? t('report.createFixSubmitting') : t('report.createFixSubmit')}
            </button>
          </div>
        </div>
      </aside>
    </>
  )
}

export default CreateFixRequestPanel
