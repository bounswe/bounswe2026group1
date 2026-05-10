import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { OBJECT_TYPES } from '../utils/objectTypeConfig.js'
import { excludedCount } from '../utils/mapFilters.js'

/**
 * MapFilters
 * Nested checkboxes for hiding map pins by category and issue.
 * Mobile (<1024px): bottom sheet matching ReportPanel's pattern.
 * Desktop: anchored popover beneath the filter button on the search bar.
 *
 * Props:
 *  - excludedTypes:  Set<string>
 *  - excludedIssues: Set<string>            (`${objectType}:${issue}`)
 *  - onChange:       (nextTypes, nextIssues) => void
 *  - onClose:        () => void
 */
function MapFilters({ excludedTypes, excludedIssues, onChange, onClose }) {
  const [expanded, setExpanded] = useState(() => new Set())
  const containerRef = useRef(null)

  const [isMobileSheet, setIsMobileSheet] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(max-width: 1023px)').matches
      : true
  )
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const mql = window.matchMedia('(max-width: 1023px)')
    function onChangeQuery() { setIsMobileSheet(mql.matches) }
    mql.addEventListener?.('change', onChangeQuery)
    return () => mql.removeEventListener?.('change', onChangeQuery)
  }, [])

  // Close on Escape; close on outside click for desktop popover.
  useEffect(() => {
    function handleKey(e) { if (e.key === 'Escape') onClose() }
    function handlePointer(e) {
      if (!isMobileSheet && containerRef.current && !containerRef.current.contains(e.target)) {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKey)
    document.addEventListener('pointerdown', handlePointer)
    return () => {
      document.removeEventListener('keydown', handleKey)
      document.removeEventListener('pointerdown', handlePointer)
    }
  }, [onClose, isMobileSheet])

  function toggleExpanded(type) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      return next
    })
  }

  function setTypeExcluded(type, excluded) {
    const nextTypes = new Set(excludedTypes)
    const nextIssues = new Set(excludedIssues)
    if (excluded) {
      // Hide entire type — drop any per-issue exclusions for it (they're redundant).
      nextTypes.add(type)
      for (const tok of excludedIssues) if (tok.startsWith(`${type}:`)) nextIssues.delete(tok)
    } else {
      nextTypes.delete(type)
    }
    onChange(nextTypes, nextIssues)
  }

  function setIssueExcluded(type, issue, excluded) {
    const nextTypes = new Set(excludedTypes)
    const nextIssues = new Set(excludedIssues)
    if (excluded) nextIssues.add(`${type}:${issue}`)
    else nextIssues.delete(`${type}:${issue}`)
    onChange(nextTypes, nextIssues)
  }

  function reset() {
    onChange(new Set(), new Set())
  }

  const total = excludedCount(excludedTypes, excludedIssues)

  // ── render ──────────────────────────────────────────────────────────────
  const wrapperStyle = isMobileSheet
    ? {
        position: 'fixed', inset: 0, zIndex: 1300,
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
        pointerEvents: 'none',
      }
    : {
        position: 'absolute', top: 'calc(100% + 8px)', right: 0,
        zIndex: 1300, pointerEvents: 'none',
      }
  const panelStyle = isMobileSheet
    ? {
        height: '70dvh', maxHeight: '70dvh', width: '100%',
        borderTopLeftRadius: 32, borderTopRightRadius: 32,
        borderTop: '1px solid rgba(172,173,173,.2)',
        boxShadow: '0 -10px 40px rgba(0,0,0,0.2)',
        pointerEvents: 'auto',
      }
    : {
        width: 360, maxHeight: 540, pointerEvents: 'auto',
        borderRadius: 24, border: '1px solid rgba(172,173,173,.2)',
        boxShadow: '0 12px 32px rgba(0,0,0,0.12)',
      }

  // On mobile we portal to document.body — the search bar wraps us in a
  // backdrop-filter ancestor, which breaks `position: fixed` (it becomes
  // a containing block of the filtered ancestor instead of the viewport).
  // Desktop popover stays in the React tree so it anchors under the button.
  const tree = (
    <div style={wrapperStyle}>
      <aside
        ref={containerRef}
        style={panelStyle}
        className="bg-surface-container-lowest flex flex-col overflow-hidden"
        role="dialog"
        aria-label="Map filters"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 flex-shrink-0 border-b border-outline-variant/15">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-on-surface-variant">tune</span>
            <h3 className="font-headline font-bold text-on-surface">Filters</h3>
            {total > 0 && (
              <span className="text-xs font-bold text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                {total} hidden
              </span>
            )}
          </div>
          <div className="flex items-center gap-1">
            {total > 0 && (
              <button
                type="button"
                onClick={reset}
                className="text-xs font-semibold text-primary hover:bg-primary/10 px-3 py-1.5 rounded-lg cursor-pointer"
              >
                Show all
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              aria-label="Close filters"
              className="w-9 h-9 rounded-full hover:bg-surface-container flex items-center justify-center cursor-pointer"
            >
              <span className="material-symbols-outlined text-on-surface-variant">close</span>
            </button>
          </div>
        </div>

        {/* List — min-h-0 lets flex-1 actually shrink and scroll inside the
            sheet on mobile; without it the list pushes past 70dvh and the
            content disappears behind the rounded corners. */}
        <div className="flex-1 min-h-0 overflow-y-auto p-3">
          <ul className="flex flex-col gap-1">
            {OBJECT_TYPES.map((t) => {
              const typeExcluded = excludedTypes.has(t.type)
              const isOpen = expanded.has(t.type)
              const perIssueExcluded = t.issues.filter((i) => excludedIssues.has(`${t.type}:${i.key}`)).length
              return (
                <li key={t.type} className="rounded-xl bg-surface-container/40">
                  <div className="flex items-center gap-2 px-3 py-2">
                    <input
                      id={`filter-type-${t.type}`}
                      type="checkbox"
                      checked={!typeExcluded}
                      onChange={(e) => setTypeExcluded(t.type, !e.target.checked)}
                      className="w-4 h-4 accent-primary cursor-pointer"
                      aria-label={`Show ${t.label}`}
                    />
                    <span className="material-symbols-outlined" style={{ fontSize: 20, color: t.markerColor }}>
                      {t.icon}
                    </span>
                    <label htmlFor={`filter-type-${t.type}`} className="flex-1 text-sm font-semibold text-on-surface cursor-pointer">
                      {t.label}
                      {!typeExcluded && perIssueExcluded > 0 && (
                        <span className="ml-2 text-[10px] font-normal text-on-surface-variant">
                          {perIssueExcluded} issue{perIssueExcluded === 1 ? '' : 's'} hidden
                        </span>
                      )}
                    </label>
                    <button
                      type="button"
                      onClick={() => toggleExpanded(t.type)}
                      aria-label={isOpen ? `Collapse ${t.label}` : `Expand ${t.label}`}
                      aria-expanded={isOpen}
                      disabled={typeExcluded}
                      className="w-7 h-7 rounded-full flex items-center justify-center hover:bg-surface-container disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-base text-on-surface-variant" style={{ transform: isOpen ? 'rotate(180deg)' : '' }}>
                        expand_more
                      </span>
                    </button>
                  </div>

                  {isOpen && !typeExcluded && (
                    <ul className="pl-12 pr-3 pb-3 flex flex-col gap-1">
                      {t.issues.map((i) => {
                        const issueId = `${t.type}:${i.key}`
                        const issueExcluded = excludedIssues.has(issueId)
                        return (
                          <li key={i.key} className="flex items-center gap-2">
                            <input
                              id={`filter-issue-${issueId}`}
                              type="checkbox"
                              checked={!issueExcluded}
                              onChange={(e) => setIssueExcluded(t.type, i.key, !e.target.checked)}
                              className="w-3.5 h-3.5 accent-primary cursor-pointer"
                              aria-label={`Show ${t.label} ${i.label}`}
                            />
                            <label htmlFor={`filter-issue-${issueId}`} className="text-xs text-on-surface cursor-pointer">
                              {i.label}
                            </label>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </li>
              )
            })}
          </ul>
        </div>
      </aside>
    </div>
  )

  if (isMobileSheet && typeof document !== 'undefined') {
    return createPortal(tree, document.body)
  }
  return tree
}

export default MapFilters
