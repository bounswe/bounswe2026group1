import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

const ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/jpg']
const MAX_BYTES = 15 * 1024 * 1024

function validateFile(file) {
  if (!ALLOWED_MIME.includes(file.type)) {
    return 'Only JPEG and PNG images are allowed.'
  }
  if (file.size > MAX_BYTES) {
    return 'Image must be 15 MB or smaller.'
  }
  return null
}

function AvatarUploader({ currentAvatarUrl, isUploading, isDeleting, onUpload, onDelete }) {
  const { t } = useTranslation()
  const [pendingFile, setPendingFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [error, setError] = useState('')
  const [failedAvatarUrl, setFailedAvatarUrl] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const dragDepthRef = useRef(0)
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (!previewUrl) return
    return () => URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  function pickFile(file) {
    if (!file) return
    const validationError = validateFile(file)
    if (validationError) {
      setError(validationError)
      setPendingFile(null)
      setPreviewUrl(null)
      return
    }
    setError('')
    setPendingFile(file)
    setPreviewUrl(URL.createObjectURL(file))
  }

  function handleInputChange(event) {
    pickFile(event.target.files?.[0])
    event.target.value = ''
  }

  function handleDrop(event) {
    event.preventDefault()
    dragDepthRef.current = 0
    setIsDragging(false)
    pickFile(event.dataTransfer.files?.[0])
  }

  function handleDragEnter(event) {
    event.preventDefault()
    dragDepthRef.current += 1
    setIsDragging(true)
  }

  function handleDragLeave(event) {
    event.preventDefault()
    dragDepthRef.current -= 1
    if (dragDepthRef.current <= 0) {
      dragDepthRef.current = 0
      setIsDragging(false)
    }
  }

  async function handleSave() {
    if (!pendingFile) return
    try {
      await onUpload(pendingFile)
      setPendingFile(null)
      setPreviewUrl(null)
    } catch (e) {
      setError(e.message || 'Failed to upload avatar.')
    }
  }

  function handleCancel() {
    setPendingFile(null)
    setPreviewUrl(null)
    setError('')
  }

  async function handleDelete() {
    if (!currentAvatarUrl) return
    if (!window.confirm('Remove your avatar?')) return
    try {
      await onDelete()
    } catch (e) {
      setError(e.message || 'Failed to remove avatar.')
    }
  }

  const avatarFailed = !!currentAvatarUrl && failedAvatarUrl === currentAvatarUrl
  const showImage = !!previewUrl || (!!currentAvatarUrl && !avatarFailed)
  const displayUrl = previewUrl || currentAvatarUrl

  return (
    <div className="flex flex-col gap-3">
      <div
        className={`flex items-center gap-4 p-3 rounded-2xl border-2 border-dashed transition-colors ${isDragging ? 'border-primary bg-primary/10' : 'border-outline-variant/40'}`}
        onDragOver={(e) => e.preventDefault()}
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <div className="w-24 h-24 rounded-full bg-primary flex items-center justify-center overflow-hidden flex-shrink-0">
          {showImage ? (
            <img
              src={displayUrl}
              alt="Avatar preview"
              className="w-full h-full object-cover"
              onError={() => { if (!previewUrl) setFailedAvatarUrl(currentAvatarUrl) }}
            />
          ) : (
            <span className="material-symbols-outlined text-white" style={{ fontSize: '48px' }}>
              person
            </span>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept={ALLOWED_MIME.join(',')}
            onChange={handleInputChange}
            className="hidden"
            data-testid="avatar-file-input"
          />
          {pendingFile ? (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleSave}
                disabled={isUploading}
                className="px-4 py-2 rounded-lg primary-gradient text-on-primary font-semibold disabled:opacity-60 cursor-pointer"
              >
                {isUploading ? t('avatar.uploading') : t('avatar.save')}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                disabled={isUploading}
                className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold cursor-pointer"
              >
                {t('avatar.cancel')}
              </button>
            </div>
          ) : (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="px-4 py-2 rounded-lg primary-gradient text-on-primary font-semibold cursor-pointer"
              >
                {currentAvatarUrl ? t('avatar.change') : t('avatar.upload')}
              </button>
              {currentAvatarUrl && (
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-semibold disabled:opacity-60 cursor-pointer"
                >
                  {isDeleting ? t('avatar.removing') : t('avatar.remove')}
                </button>
              )}
            </div>
          )}
          <p className="text-xs text-on-surface-variant">
            {isDragging ? t('avatar.dropToPreview') : t('avatar.hint')}
          </p>
        </div>
      </div>

      {error && (
        <p role="alert" className="text-sm text-error">
          {error}
        </p>
      )}
    </div>
  )
}

export default AvatarUploader
