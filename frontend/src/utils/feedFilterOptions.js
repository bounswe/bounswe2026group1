/**
 * Centralized option lists for the report feed filters. Keep labels in sync with
 * the backend enums (ReportStatus, FeedSort).
 */

export const STATUS_OPTIONS = [
  { id: 'PENDING', label: 'Pending' },
  { id: 'VERIFIED', label: 'Validated' },
  { id: 'FIXED', label: 'Fixed' },
  { id: 'REJECTED', label: 'Rejected' },
]

export const SORT_OPTIONS = [
  { id: 'NEWEST', label: 'Newest' },
  { id: 'OLDEST', label: 'Oldest' },
  { id: 'MOST_AGREED', label: 'Most agreed' },
  { id: 'MOST_VOTED', label: 'Most voted' },
  { id: 'DISTANCE', label: 'Nearest (needs location)' },
]
