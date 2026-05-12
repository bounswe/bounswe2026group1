// Schema.org JSON-LD helpers for report pages.
//
// Each report is published as a Place entity with `geo` coordinates plus the
// accessibility metadata properties `accessibilityFeature` (positive features
// the report references via its ObjectTypes) and `accessibilityHazard` (the
// IssueType-level problems being reported). Tokens are camelCase per the
// Schema.org accessibility-property convention so the JSON-LD parses cleanly
// in Google's Rich Results test even though Schema.org doesn't formally
// constrain the value vocabulary for physical Places yet.

export const OBJECT_TYPE_TO_FEATURE = {
  RAMP: 'ramp',
  ELEVATOR: 'elevator',
  SIDEWALK: 'sidewalk',
  DOOR: 'accessibleDoor',
  STAIR: 'staircase',
  PEDESTRIAN_CROSSING: 'pedestrianCrossing',
  CURB_RAMP: 'curbRamp',
  WASHROOM: 'accessibleWashroom',
  ROOM_SIGN: 'brailleSignage',
}

export const ISSUE_TYPE_TO_HAZARD = {
  MISSING: 'missingFeature',
  TOO_STEEP: 'steepGradient',
  TOO_NARROW: 'tooNarrow',
  MISSING_HANDRAIL: 'missingHandrail',
  NO_LANDING: 'noLanding',
  SLIPPERY_SURFACE: 'slipperySurface',
  HANDRAIL_TOO_LOW: 'handrailTooLow',
  INSUFFICIENT_LANDING_AREA: 'insufficientLandingArea',
  OUT_OF_SERVICE: 'outOfService',
  DOOR_TOO_NARROW: 'doorTooNarrow',
  CABIN_TOO_SMALL: 'cabinTooSmall',
  NO_AUDIO: 'noAudioCue',
  NO_GRAB_BAR: 'noGrabBar',
  INSUFFICIENT_LANDING: 'insufficientLanding',
  NO_BRAILLE: 'noBrailleSignage',
  BUTTON_HEIGHT_INVALID: 'buttonHeightInvalid',
  INSUFFICIENT_LIGHTING: 'insufficientLighting',
  BLOCKED: 'blockedPath',
  NO_TACTILE_PAVING: 'noTactilePaving',
  INSUFFICIENT_CLEARANCE: 'insufficientClearance',
  UNEVEN_SURFACE: 'unevenSurface',
  UNRAMPED_LEVEL_DIFFERENCE: 'unrampedLevelDifference',
  HIGH_THRESHOLD: 'highThreshold',
  STEP_AT_ENTRANCE: 'stepAtEntrance',
  NO_LEVER_HANDLE: 'noLeverHandle',
  HEAVY_DOOR: 'heavyDoor',
  NO_AUTOMATIC_DOOR: 'noAutomaticDoor',
  NO_GLASS_MARKING: 'noGlassMarking',
  HANDLE_HEIGHT_INVALID: 'handleHeightInvalid',
  INTERCOM_INACCESSIBLE: 'intercomInaccessible',
  RISER_TOO_HIGH: 'riserTooHigh',
  TREAD_TOO_SHALLOW: 'treadTooShallow',
  NO_ANTI_SLIP: 'noAntiSlip',
  OPEN_RISERS: 'openRisers',
  IRREGULAR_STEPS: 'irregularSteps',
  MISSING_NOSING_STRIP: 'missingNosingStrip',
  NO_AUDIO_SIGNAL: 'noAudioSignal',
  SIGNAL_TOO_SHORT: 'signalTooShort',
  NO_DROPPED_CURB: 'noDroppedCurb',
  FADED_MARKINGS: 'fadedMarkings',
  NO_PEDESTRIAN_REFUGE: 'noPedestrianRefuge',
  NO_TACTILE_WARNING: 'noTactileWarning',
  RAISED_LIP: 'raisedLip',
  NO_ACCESSIBLE_STALL: 'noAccessibleStall',
  INSUFFICIENT_TURNING_SPACE: 'insufficientTurningSpace',
  SINK_TOO_HIGH: 'sinkTooHigh',
  NO_EMERGENCY_BUTTON: 'noEmergencyButton',
  LOW_CONTRAST: 'lowContrast',
  TEXT_TOO_SMALL: 'textTooSmall',
  INVALID_HEIGHT: 'invalidHeight',
}

function isFiniteNumber(value) {
  if (value == null || value === '') return false
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n)
}

function uniqueOrdered(values) {
  return [...new Set(values)]
}

/**
 * Build a Schema.org Place JSON-LD object describing a single report.
 *
 * Returns null when the report is missing a usable identifier — callers should
 * skip rendering the script tag in that case. All other fields are added only
 * when present so we don't pollute the output with empty arrays.
 */
export function buildReportJsonLd(report, { url } = {}) {
  if (!report || report.id == null) return null

  const features = []
  const hazards = []
  for (const obj of report.objects ?? []) {
    const feature = OBJECT_TYPE_TO_FEATURE[obj.objectType]
    if (feature) features.push(feature)
    for (const issueKey of obj.issues ?? []) {
      const hazard = ISSUE_TYPE_TO_HAZARD[issueKey]
      if (hazard) hazards.push(hazard)
    }
  }

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Place',
    '@id': `mapcess:report:${report.id}`,
  }

  if (report.title) jsonLd.name = report.title
  if (report.description) jsonLd.description = report.description

  if (isFiniteNumber(report.latitude) && isFiniteNumber(report.longitude)) {
    jsonLd.geo = {
      '@type': 'GeoCoordinates',
      latitude: Number(report.latitude),
      longitude: Number(report.longitude),
    }
  }

  if (features.length) jsonLd.accessibilityFeature = uniqueOrdered(features)
  if (hazards.length) jsonLd.accessibilityHazard = uniqueOrdered(hazards)
  if (url) jsonLd.url = url

  return jsonLd
}

/**
 * Serialize a report's JSON-LD to a string suitable for embedding inside a
 * <script type="application/ld+json"> tag. Returns an empty string when the
 * report cannot produce a usable payload so the caller can skip rendering.
 *
 * The output escapes `</` to prevent a description containing `</script>`
 * from prematurely closing the surrounding script element.
 */
export function reportJsonLdString(report, opts) {
  const obj = buildReportJsonLd(report, opts)
  if (!obj) return ''
  return JSON.stringify(obj).replace(/</g, '\\u003c')
}
