/**
 * parseVoiceCommand — pure, deterministic mapping from a recognized phrase to
 * a structured app command. The dispatcher consumes the result to invoke
 * existing app actions.
 *
 * Recognized intents (1.1.3.8 / issue #287):
 *   "zoom in" / "zoom out" / "my location"      → map controls
 *   "find <q>" / "search <q>"                   → MapSearchBar invocation
 *   "navigate to <q>" / "go to <q>"             → route planning
 *   "report <category> here" / "report here"    → opens create-report panel
 *
 * Everything else returns { type: 'UNKNOWN', raw } so callers can surface a
 * "command not recognized" announcement to the ARIA live region.
 */

const NORMALIZE = (input) =>
  (input ?? '')
    .toLowerCase()
    .trim()
    .replace(/[.,!?;:]+$/g, '')
    .replace(/\s+/g, ' ')

export function parseVoiceCommand(input) {
  const raw = (input ?? '').toString()
  const text = NORMALIZE(raw)
  if (!text) return { type: 'UNKNOWN', raw }

  // Map controls — exact-match short verbs come first so they don't fall into
  // the longer "navigate to <X>" branch by accident.
  if (text === 'zoom in') return { type: 'ZOOM_IN' }
  if (text === 'zoom out') return { type: 'ZOOM_OUT' }
  if (text === 'my location' || text === 'locate me' || text === 'where am i') {
    return { type: 'LOCATE_ME' }
  }

  // Report variants — handle "report here" first (no category) before the
  // "report <cat> here" prefix-match below.
  if (text === 'report here' || text === 'report obstacle' || text === 'report an issue') {
    return { type: 'REPORT', category: null }
  }
  const reportHere = text.match(/^report\s+(.+?)\s+here$/)
  if (reportHere) return { type: 'REPORT', category: reportHere[1].trim() }

  // Navigation: "navigate to <X>" / "go to <X>".
  const navMatch = text.match(/^(?:navigate to|go to)\s+(.+)$/)
  if (navMatch) return { type: 'NAVIGATE', dest: navMatch[1].trim() }

  // Search: "find <X>" / "search <X>" / "search for <X>".
  const searchMatch = text.match(/^(?:find|search(?: for)?)\s+(.+)$/)
  if (searchMatch) return { type: 'SEARCH', query: searchMatch[1].trim() }

  return { type: 'UNKNOWN', raw }
}
