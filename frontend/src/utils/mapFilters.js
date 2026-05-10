// Filter model for the map sidebar.
//
// Two excluded sets:
//   - excludedTypes:  Set<objectType>            (e.g. 'RAMP')      hides every report whose objects are all of this type
//   - excludedIssues: Set<`${objectType}:${issue}`>                  hides only that issue under that type
//
// URL serialization uses `?excluded=RAMP,STAIR:CRACKED,DOOR:LOCKED`.
// A wildcard token (just `RAMP`) lives in excludedTypes; a token with `:`
// (e.g. `STAIR:CRACKED`) lives in excludedIssues. This lets a user share
// a filtered view without us inventing two separate URL params.

export function parseExcluded(searchParam) {
  const types = new Set()
  const issues = new Set()
  if (!searchParam) return { types, issues }
  for (const token of searchParam.split(',')) {
    const trimmed = token.trim()
    if (!trimmed) continue
    if (trimmed.includes(':')) issues.add(trimmed)
    else types.add(trimmed)
  }
  return { types, issues }
}

export function serializeExcluded(types, issues) {
  return [...types, ...issues].join(',')
}

/** A single report-object is visible if its type isn't fully excluded AND
 *  (it has no issues at all — e.g. a FEATURE report — or at least one of
 *  its issues isn't excluded). */
export function isObjectVisible(obj, excludedTypes, excludedIssues) {
  if (excludedTypes.has(obj.objectType)) return false
  if (!obj.issues || obj.issues.length === 0) return true
  return obj.issues.some((i) => !excludedIssues.has(`${obj.objectType}:${i}`))
}

/** A report is visible if any of its objects is visible. Reports without
 *  any objects (legacy or edge-case payloads) stay visible by default so
 *  filtering only ever hides — never silently drops legitimate data. */
export function isReportVisible(report, excludedTypes, excludedIssues) {
  if (!report.objects || report.objects.length === 0) return true
  return report.objects.some((o) => isObjectVisible(o, excludedTypes, excludedIssues))
}

/** Total count of exclusions, for the "active filter" badge on the button. */
export function excludedCount(excludedTypes, excludedIssues) {
  return excludedTypes.size + excludedIssues.size
}
