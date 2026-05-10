import { Link } from 'react-router-dom'
import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import { isVideoUrl } from '../utils/mediaType.js'
import { useUserProfile } from '../hooks/useUserProfile.js'

function ObjectTag({ objectType }) {
  const cfg = OBJECT_TYPE_MAP[objectType]
  const label = cfg?.label ?? objectType
  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-surface-container text-on-surface text-[11px] font-semibold"
    >
      {cfg?.icon && (
        <span
          className="material-symbols-outlined"
          style={{ fontSize: '14px', color: cfg.markerColor }}
        >
          {cfg.icon}
        </span>
      )}
      {label}
    </span>
  )
}

function MediaPreview({ url, alt }) {
  if (!url) {
    return (
      <div className="w-full h-44 bg-surface-container rounded-xl flex items-center justify-center">
        <span className="material-symbols-outlined text-5xl text-outline-variant">image</span>
      </div>
    )
  }
  if (isVideoUrl(url)) {
    return (
      <video
        className="w-full h-44 object-cover rounded-xl bg-black"
        src={url}
        controls
        playsInline
      />
    )
  }
  return (
    <img
      className="w-full h-44 object-cover rounded-xl"
      src={url}
      alt={alt}
      loading="lazy"
    />
  )
}

function Reporter({ ownerId, fallbackName }) {
  const { data } = useUserProfile(ownerId)
  const name = data?.name || fallbackName
  const avatarUrl = data?.avatarUrl
  return (
    <div className="flex items-center gap-2">
      <div className="w-8 h-8 rounded-full bg-surface-container-high overflow-hidden flex items-center justify-center flex-shrink-0">
        {avatarUrl ? (
          <img src={avatarUrl} alt={name} className="w-full h-full object-cover" />
        ) : (
          <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '18px' }}>
            person
          </span>
        )}
      </div>
      <span className="text-sm font-semibold text-on-surface truncate">{name}</span>
    </div>
  )
}

/**
 * ReportCard
 * Compact, link-style card for the Feed page. Click → opens the report on
 * the map via the existing `/?report=:id` deep-link.
 */
function ReportCard({ report }) {
  const objectTypes = [...new Set((report.objects || []).map((o) => o.objectType).filter(Boolean))]

  return (
    <Link
      to={`/?report=${report.id}`}
      className="bg-surface-container-lowest rounded-2xl shadow-sm hover:shadow-md transition-shadow p-4 flex flex-col gap-3"
    >
      <div className="flex items-center justify-between gap-2">
        <Reporter ownerId={report.ownerId} fallbackName={report.reportedBy} />
        <span className="text-xs text-on-surface-variant flex-shrink-0">{report.date}</span>
      </div>

      <MediaPreview url={report.image} alt={report.title} />

      <h3 className="font-headline font-bold text-on-surface text-base leading-tight">
        {report.title}
      </h3>
      <p className="text-sm text-on-surface-variant line-clamp-3 break-words">
        {report.description}
      </p>

      {objectTypes.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {objectTypes.map((t) => (
            <ObjectTag key={t} objectType={t} />
          ))}
        </div>
      )}

      <div className="flex items-center gap-4 text-xs text-on-surface-variant pt-1 border-t border-outline-variant/15">
        <span className="inline-flex items-center gap-1">
          <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>thumb_up</span>
          {report.agrees ?? 0}
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>thumb_down</span>
          {report.disagrees ?? 0}
        </span>
        <span className="inline-flex items-center gap-1 ml-auto">
          <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>place</span>
          {report.location}
        </span>
      </div>
    </Link>
  )
}

export default ReportCard
