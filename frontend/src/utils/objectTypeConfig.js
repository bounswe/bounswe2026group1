export const OBJECT_TYPES = [
  {
    type: 'RAMP',
    label: 'Ramp',
    icon: 'accessible_forward',
    issues: [
      { key: 'MISSING',          label: 'Missing' },
      { key: 'TOO_STEEP',        label: 'Too Steep' },
      { key: 'TOO_NARROW',       label: 'Too Narrow' },
      { key: 'MISSING_HANDRAIL', label: 'Missing Handrail' },
      { key: 'NO_LANDING',       label: 'No Landing' },
      { key: 'SLIPPERY_SURFACE', label: 'Slippery Surface' },
    ],
    measurements: [
      { key: 'slope_percent', label: 'Slope',  unit: '%',  accessible_max: 10 },
      { key: 'width_cm',      label: 'Width',  unit: 'cm', accessible_min: 100 },
      { key: 'height_cm',     label: 'Height', unit: 'cm' },
    ],
  },
  {
    type: 'ELEVATOR',
    label: 'Elevator',
    icon: 'elevator',
    issues: [
      { key: 'MISSING',              label: 'Missing' },
      { key: 'OUT_OF_SERVICE',       label: 'Out of Service' },
      { key: 'DOOR_TOO_NARROW',      label: 'Door Too Narrow' },
      { key: 'CABIN_TOO_SMALL',      label: 'Cabin Too Small' },
      { key: 'NO_AUDIO',             label: 'No Audio' },
      { key: 'NO_GRAB_BAR',          label: 'No Grab Bar' },
      { key: 'INSUFFICIENT_LANDING', label: 'Insufficient Landing' },
    ],
    measurements: [
      { key: 'door_width_cm',  label: 'Door Width',  unit: 'cm', accessible_min: 90 },
      { key: 'cabin_width_cm', label: 'Cabin Width', unit: 'cm', accessible_min: 120 },
      { key: 'cabin_depth_cm', label: 'Cabin Depth', unit: 'cm', accessible_min: 140 },
    ],
  },
  {
    type: 'SIDEWALK',
    label: 'Sidewalk',
    icon: 'directions_walk',
    issues: [
      { key: 'MISSING',                label: 'Missing' },
      { key: 'TOO_NARROW',             label: 'Too Narrow' },
      { key: 'SLIPPERY_SURFACE',       label: 'Slippery Surface' },
      { key: 'BLOCKED',                label: 'Blocked' },
      { key: 'NO_TACTILE_PAVING',      label: 'No Tactile Paving' },
      { key: 'INSUFFICIENT_CLEARANCE', label: 'Insufficient Clearance' },
    ],
    measurements: [
      { key: 'height_cm', label: 'Curb Height', unit: 'cm', accessible_max: 15 },
      { key: 'width_cm',  label: 'Width',        unit: 'cm', accessible_min: 150 },
    ],
  },
  {
    type: 'DOOR',
    label: 'Door',
    icon: 'door_front',
    issues: [
      { key: 'MISSING',           label: 'Missing' },
      { key: 'TOO_NARROW',        label: 'Too Narrow' },
      { key: 'HIGH_THRESHOLD',    label: 'High Threshold' },
      { key: 'STEP_AT_ENTRANCE',  label: 'Step at Entrance' },
      { key: 'NO_LEVER_HANDLE',   label: 'No Lever Handle' },
      { key: 'HEAVY_DOOR',        label: 'Heavy Door' },
      { key: 'NO_AUTOMATIC_DOOR', label: 'No Automatic Door' },
    ],
    measurements: [
      { key: 'width_cm',            label: 'Width',            unit: 'cm', accessible_min: 90 },
      { key: 'threshold_height_cm', label: 'Threshold Height', unit: 'cm', accessible_max: 0.6 },
    ],
  },
  {
    type: 'STAIR',
    label: 'Stair',
    icon: 'stairs',
    issues: [
      { key: 'MISSING',          label: 'Missing' },
      { key: 'TOO_NARROW',       label: 'Too Narrow' },
      { key: 'MISSING_HANDRAIL', label: 'Missing Handrail' },
      { key: 'NO_LANDING',       label: 'No Landing' },
      { key: 'SLIPPERY_SURFACE', label: 'Slippery Surface' },
      { key: 'RISER_TOO_HIGH',   label: 'Riser Too High' },
      { key: 'TREAD_TOO_SHALLOW',label: 'Tread Too Shallow' },
      { key: 'NO_ANTI_SLIP',     label: 'No Anti-Slip' },
      { key: 'OPEN_RISERS',      label: 'Open Risers' },
    ],
    measurements: [
      { key: 'riser_cm', label: 'Riser Height', unit: 'cm', accessible_max: 16 },
      { key: 'tread_cm', label: 'Tread Depth',  unit: 'cm', accessible_min: 27 },
    ],
  },
]

export const OBJECT_TYPE_MAP = Object.fromEntries(OBJECT_TYPES.map(t => [t.type, t]))
