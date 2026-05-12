import { describe, it, expect } from 'vitest'
import { STATUS_OPTIONS, SORT_OPTIONS } from './feedFilterOptions.js'

describe('feedFilterOptions', () => {
  it('exports status ids aligned with backend enums', () => {
    const ids = STATUS_OPTIONS.map((o) => o.id)
    expect(ids).toContain('PENDING')
    expect(ids).toContain('VERIFIED')
    expect(ids).toContain('FIXED')
    expect(ids).toContain('REJECTED')
  })

  it('exports sort ids including distance', () => {
    const ids = SORT_OPTIONS.map((o) => o.id)
    expect(ids).toContain('DISTANCE')
    expect(ids).toContain('MOST_AGREED')
  })
})
