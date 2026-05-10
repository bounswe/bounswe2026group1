import { describe, it, expect } from 'vitest'
import {
  parseExcluded,
  serializeExcluded,
  isObjectVisible,
  isReportVisible,
  excludedCount,
} from './mapFilters.js'

describe('parseExcluded', () => {
  it('returns empty sets when input is missing or empty', () => {
    for (const v of [null, undefined, '', '   ']) {
      const { types, issues } = parseExcluded(v)
      expect(types.size).toBe(0)
      expect(issues.size).toBe(0)
    }
  })

  it('separates whole-type tokens from per-issue tokens', () => {
    const { types, issues } = parseExcluded('RAMP,STAIR:CRACKED,DOOR:LOCKED')
    expect([...types].sort()).toEqual(['RAMP'])
    expect([...issues].sort()).toEqual(['DOOR:LOCKED', 'STAIR:CRACKED'])
  })

  it('ignores blank tokens', () => {
    const { types, issues } = parseExcluded(',RAMP,,STAIR:CRACKED,')
    expect(types.size).toBe(1)
    expect(issues.size).toBe(1)
  })
})

describe('serializeExcluded', () => {
  it('produces a comma-joined token round-trippable through parse', () => {
    const types = new Set(['RAMP'])
    const issues = new Set(['STAIR:CRACKED'])
    const out = serializeExcluded(types, issues)
    expect(out.split(',').sort()).toEqual(['RAMP', 'STAIR:CRACKED'])

    const parsed = parseExcluded(out)
    expect([...parsed.types]).toEqual([...types])
    expect([...parsed.issues]).toEqual([...issues])
  })

  it('returns empty string when nothing is excluded', () => {
    expect(serializeExcluded(new Set(), new Set())).toBe('')
  })
})

describe('isObjectVisible', () => {
  it('hides when type is excluded', () => {
    const obj = { objectType: 'RAMP', issues: ['MISSING'] }
    expect(isObjectVisible(obj, new Set(['RAMP']), new Set())).toBe(false)
  })

  it('shows when no exclusions match', () => {
    const obj = { objectType: 'RAMP', issues: ['MISSING'] }
    expect(isObjectVisible(obj, new Set(), new Set())).toBe(true)
  })

  it('shows objects with no issues (FEATURE) regardless of issue exclusions', () => {
    const obj = { objectType: 'RAMP', issues: [] }
    expect(isObjectVisible(obj, new Set(), new Set(['RAMP:MISSING']))).toBe(true)
  })

  it('hides only when EVERY issue is excluded', () => {
    const obj = { objectType: 'STAIR', issues: ['CRACKED', 'NO_HANDRAIL'] }
    expect(isObjectVisible(obj, new Set(), new Set(['STAIR:CRACKED']))).toBe(true)
    expect(isObjectVisible(obj, new Set(), new Set(['STAIR:CRACKED', 'STAIR:NO_HANDRAIL']))).toBe(false)
  })
})

describe('isReportVisible', () => {
  it('shows reports with no objects (legacy / edge cases)', () => {
    expect(isReportVisible({ objects: [] }, new Set(['RAMP']), new Set())).toBe(true)
    expect(isReportVisible({}, new Set(['RAMP']), new Set())).toBe(true)
  })

  it('shows reports if any object is visible', () => {
    const report = {
      objects: [
        { objectType: 'RAMP', issues: ['MISSING'] },
        { objectType: 'STAIR', issues: ['CRACKED'] },
      ],
    }
    expect(isReportVisible(report, new Set(['RAMP']), new Set())).toBe(true)
  })

  it('hides reports only when every object is hidden', () => {
    const report = {
      objects: [
        { objectType: 'RAMP', issues: ['MISSING'] },
        { objectType: 'STAIR', issues: ['CRACKED'] },
      ],
    }
    expect(isReportVisible(report, new Set(['RAMP', 'STAIR']), new Set())).toBe(false)
  })
})

describe('excludedCount', () => {
  it('sums type and issue exclusions', () => {
    expect(excludedCount(new Set(), new Set())).toBe(0)
    expect(excludedCount(new Set(['RAMP']), new Set(['STAIR:CRACKED']))).toBe(2)
  })
})
