import { describe, it, expect } from 'vitest'
import {
  CONSTRAINT_OBJECT_TYPES,
  MULTI_SURFACE_KEY,
  bucketKeyFor,
} from './routingConstraintObjects.js'

describe('routingConstraintObjects', () => {
  it('maps known constraints to a single surface bucket', () => {
    expect(bucketKeyFor('AVOID_STAIRS')).toBe('STAIR')
    expect(bucketKeyFor('REQUIRE_CURB_RAMPS')).toBe('CURB_RAMP')
  })

  it('maps multi-type constraints to MULTI_SURFACE_KEY', () => {
    expect(bucketKeyFor('AVOID_SLIPPERY_SURFACES')).toBe(MULTI_SURFACE_KEY)
    expect(CONSTRAINT_OBJECT_TYPES.AVOID_SLIPPERY_SURFACES.length).toBeGreaterThan(1)
  })

  it('falls back for unknown constraint names', () => {
    expect(bucketKeyFor('UNKNOWN_CONSTRAINT')).toBe(MULTI_SURFACE_KEY)
  })
})
