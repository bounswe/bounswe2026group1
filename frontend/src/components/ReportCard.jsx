import { OBJECT_TYPE_MAP } from '../utils/objectTypeConfig.js'
import { formatDistanceKm } from '../utils/geo.js'

/**
 * Compact report preview for list feeds (design aligned with ReportPanel / Home).
 */
function ReportCard({
  report,
  distanceKm = null,
  showProximity = false,
  onCardClick,
}) {
  const cfg = OBJECT_TYPE_MAP[report.primaryObjectType] ?? {
    icon: 'report',
    markerColor: '#767777',
  }
  const isFixed = report.status === 'fixed'
  const isRejected = report.status === 'rejected'
  const isValidated = report.status === 'verified'

  return (
    <article
      role="button"
      tabIndex={0}
      onClick={() => onCardClick?.()}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onCardClick?.()
        }
      }}
      className="
        group flex min-w-0 cursor-pointer flex-col overflow-hidden rounded-md border border-outline-variant/25 bg-white/85 backdrop-blur-md text-left
        shadow-[0_3px_10px_rgb(0,0,0,0.045)] transition-all duration-150
        hover:border-primary/35 hover:shadow-[0_6px_14px_rgb(23,106,33,0.09)] hover:-translate-y-0.5
        focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary
      "
    >
      <div className="relative aspect-[3/1] max-h-[4.75rem] w-full overflow-hidden bg-surface-container sm:max-h-[5rem]">
        {report.image ? (
          <img
            src={report.image}
            alt=""
            className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.03]"
          />
        ) : (
          <div
            className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary/8 to-tertiary/10"
            aria-hidden
          >
            <span className="material-symbols-outlined text-2xl text-primary/35" style={{ fontVariationSettings: "'FILL' 1" }}>
              {cfg.icon}
            </span>
          </div>
        )}
        {showProximity && distanceKm != null && (
          <div className="absolute left-1.5 top-1.5 flex flex-wrap gap-1">
            <span className="rounded-full bg-primary px-1 py-0.5 text-[8px] font-bold uppercase tracking-wide text-on-primary shadow-sm">
              Nearby · {formatDistanceKm(distanceKm)}
            </span>
          </div>
        )}
        <div className="absolute right-1.5 top-1.5">
          <span
            className={`text-[8px] font-bold px-1 py-0.5 rounded-full uppercase tracking-wider shadow-sm ring-1 ring-white/30 ${
              isFixed
                ? 'bg-emerald-600 text-white'
                : isRejected
                  ? 'bg-error text-on-error'
                  : isValidated
                    ? 'bg-primary-container text-on-primary-container'
                    : 'bg-amber-100 text-amber-900'
            }`}
          >
            {isFixed ? 'Fixed' : isRejected ? 'Rejected' : isValidated ? 'Validated' : 'Unverified'}
          </span>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-1.5 p-2.5">
        <div className="flex items-start gap-2">
          <div
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary transition group-hover:bg-primary/15"
            aria-hidden
          >
            <span className="material-symbols-outlined text-[16px]" style={{ fontVariationSettings: "'FILL' 1", color: cfg.markerColor }}>
              {cfg.icon}
            </span>
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="font-headline text-[11px] font-extrabold leading-snug text-on-surface group-hover:text-primary transition-colors line-clamp-2 sm:text-[12px]">
              {report.title}
            </h2>
            <p className="mt-0.5 text-[9px] text-on-surface-variant">{report.date}</p>
          </div>
        </div>
        {report.description && (
          <p className="line-clamp-1 text-[10px] leading-snug text-on-surface-variant">{report.description}</p>
        )}
        <div className="mt-auto flex items-center justify-between border-t border-outline-variant/15 pt-1 text-[9px] text-on-surface-variant">
          <span className="flex items-center gap-0.5">
            <span className="material-symbols-outlined text-[12px] text-secondary">thumb_up</span>
            <span className="font-semibold text-on-surface">{report.agrees ?? 0}</span>
            <span className="text-on-surface-variant">agrees</span>
          </span>
        </div>
      </div>
    </article>
  )
}

export default ReportCard
