function Pagination({ page, totalPages, onPageChange }) {
  const safeTotal = Math.max(1, totalPages || 1)
  const current = Math.max(0, Math.min(page, safeTotal - 1))
  const canPrev = current > 0
  const canNext = current < safeTotal - 1

  return (
    <div className="flex items-center justify-between gap-3 mt-4 text-sm text-on-surface-variant">
      <div>
        Page <span className="font-semibold text-on-surface">{current + 1}</span> of{' '}
        <span className="font-semibold text-on-surface">{safeTotal}</span>
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => canPrev && onPageChange(current - 1)}
          disabled={!canPrev}
          className="px-3 py-1.5 rounded-lg border border-outline-variant disabled:opacity-40 disabled:cursor-not-allowed hover:bg-surface-container transition-colors"
        >
          Previous
        </button>
        <button
          type="button"
          onClick={() => canNext && onPageChange(current + 1)}
          disabled={!canNext}
          className="px-3 py-1.5 rounded-lg border border-outline-variant disabled:opacity-40 disabled:cursor-not-allowed hover:bg-surface-container transition-colors"
        >
          Next
        </button>
      </div>
    </div>
  )
}

export default Pagination
