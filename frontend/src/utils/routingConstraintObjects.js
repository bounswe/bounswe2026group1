// Mirrors backend's RoutingConstraint hazard map (RoutingConstraint.java).
// Lets the UI group constraints by the ObjectType they actually affect, and
// surface the matching icon/colour from objectTypeConfig.js. Kept in sync
// manually — when the backend enum gets a new constraint or shifts hazards,
// update the relevant array here.

export const CONSTRAINT_OBJECT_TYPES = {
  AVOID_STAIRS: ['STAIR'],
  REQUIRE_RAMPS: ['RAMP'],
  AVOID_UNSAFE_RAMPS: ['RAMP'],
  AVOID_NARROW_SIDEWALKS: ['SIDEWALK'],
  AVOID_LOW_CLEARANCE: ['SIDEWALK'],
  AVOID_SLIPPERY_SURFACES: ['RAMP', 'SIDEWALK', 'STAIR'],
  REQUIRE_HANDRAILS: ['RAMP', 'STAIR'],
  AVOID_BLOCKED_OR_MISSING_SIDEWALKS: ['SIDEWALK'],
  AVOID_UNSAFE_STAIRS: ['STAIR'],
  AVOID_MISSING_TACTILE_PAVING: ['SIDEWALK'],
  REQUIRE_CURB_RAMPS: ['CURB_RAMP'],
  AVOID_INACCESSIBLE_CROSSINGS: ['PEDESTRIAN_CROSSING'],
}

export const MULTI_SURFACE_KEY = '__MULTI_SURFACE__'

/**
 * Returns the bucket key a constraint belongs to in the modal's grouped UI.
 * Constraints affecting one ObjectType bucket under that type; constraints
 * spanning multiple ObjectTypes bucket under MULTI_SURFACE_KEY so they are
 * still discoverable without being repeated.
 */
export function bucketKeyFor(constraintName) {
  const types = CONSTRAINT_OBJECT_TYPES[constraintName]
  if (!types || types.length === 0) return MULTI_SURFACE_KEY
  if (types.length > 1) return MULTI_SURFACE_KEY
  return types[0]
}
