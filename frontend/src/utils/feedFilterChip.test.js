import { describe, it, expect } from 'vitest'
import { feedFilterChipClass } from './feedFilterChip.js'

describe('feedFilterChipClass', () => {
  it('returns active styling when active', () => {
    expect(feedFilterChipClass(true)).toMatch(/bg-primary/)
    expect(feedFilterChipClass(true)).toMatch(/text-on-primary/)
  })

  it('returns inactive styling when false', () => {
    expect(feedFilterChipClass(false)).toMatch(/bg-surface-container-highest/)
    expect(feedFilterChipClass(false)).toMatch(/hover:bg-primary/)
  })

  it('includes focus ring utilities', () => {
    expect(feedFilterChipClass(false)).toMatch(/focus-visible:ring-primary/)
  })
})
