export const OBJECT_TYPES = [
  {
    type: 'RAMP',
    label: 'Ramp',
    icon: 'accessible_forward',
    markerColor: '#2E7D32',
    environments: ['OUTDOOR', 'INDOOR'],
    issues: [
      { key: 'MISSING',                   label: 'Missing' },
      { key: 'TOO_STEEP',                 label: 'Too Steep' },
      { key: 'TOO_NARROW',                label: 'Too Narrow' },
      { key: 'MISSING_HANDRAIL',          label: 'Missing Handrail' },
      { key: 'NO_LANDING',                label: 'No Landing' },
      { key: 'SLIPPERY_SURFACE',          label: 'Slippery Surface' },
      { key: 'HANDRAIL_TOO_LOW',          label: 'Handrail Too Low' },
      { key: 'INSUFFICIENT_LANDING_AREA', label: 'Insufficient Landing Area' },
    ],
    measurements: [
      { key: 'slope_percent', label: 'Slope',  unit: '%',  accessible_max: 10,  description: 'Steepness of the ramp. A wheelchair user can manage up to about 10% (a 1 cm rise per 10 cm of length) without exhausting effort.' },
      { key: 'width_cm',      label: 'Width',  unit: 'cm', accessible_min: 100, description: 'Clear travel width. 100 cm lets a single wheelchair pass; wider lets two people pass each other.' },
      { key: 'height_cm',     label: 'Height', unit: 'cm',                      description: 'Total vertical rise the ramp covers, used to gauge whether the slope is sustainable over its length.' },
    ],
  },
  {
    type: 'ELEVATOR',
    label: 'Elevator',
    icon: 'elevator',
    markerColor: '#B02500',
    environments: ['OUTDOOR', 'INDOOR'],
    issues: [
      { key: 'MISSING',               label: 'Missing' },
      { key: 'OUT_OF_SERVICE',        label: 'Out of Service' },
      { key: 'DOOR_TOO_NARROW',       label: 'Door Too Narrow' },
      { key: 'CABIN_TOO_SMALL',       label: 'Cabin Too Small' },
      { key: 'NO_AUDIO',              label: 'No Audio' },
      { key: 'NO_GRAB_BAR',           label: 'No Grab Bar' },
      { key: 'INSUFFICIENT_LANDING',  label: 'Insufficient Landing' },
      { key: 'NO_BRAILLE',            label: 'No Braille' },
      { key: 'BUTTON_HEIGHT_INVALID', label: 'Button Height Invalid' },
      { key: 'INSUFFICIENT_LIGHTING', label: 'Insufficient Lighting' },
    ],
    measurements: [
      { key: 'door_width_cm',  label: 'Door Width',  unit: 'cm', accessible_min: 90,  description: 'Clear opening when the doors are fully open. 90 cm fits a standard wheelchair plus its handgrips.' },
      { key: 'cabin_width_cm', label: 'Cabin Width', unit: 'cm', accessible_min: 120, description: 'Inside width of the cabin. 120 cm gives a wheelchair user enough room to fit comfortably.' },
      { key: 'cabin_depth_cm', label: 'Cabin Depth', unit: 'cm', accessible_min: 140, description: 'Inside depth front-to-back. 140 cm lets a wheelchair turn or fit beside another passenger.' },
    ],
  },
  {
    type: 'SIDEWALK',
    label: 'Sidewalk',
    icon: 'directions_walk',
    markerColor: '#495F69',
    environments: ['OUTDOOR'],
    issues: [
      { key: 'MISSING',                   label: 'Missing' },
      { key: 'TOO_NARROW',                label: 'Too Narrow' },
      { key: 'SLIPPERY_SURFACE',          label: 'Slippery Surface' },
      { key: 'BLOCKED',                   label: 'Blocked' },
      { key: 'NO_TACTILE_PAVING',         label: 'No Tactile Paving' },
      { key: 'INSUFFICIENT_CLEARANCE',    label: 'Insufficient Clearance' },
      { key: 'UNEVEN_SURFACE',            label: 'Uneven Surface' },
      { key: 'UNRAMPED_LEVEL_DIFFERENCE', label: 'Unramped Level Difference' },
    ],
    measurements: [
      { key: 'height_cm', label: 'Curb Height', unit: 'cm', accessible_max: 15,  description: 'Step from sidewalk to road. Above ~15 cm without a curb ramp blocks wheels and walkers.' },
      { key: 'width_cm',  label: 'Width',       unit: 'cm', accessible_min: 150, description: 'Usable walking width (ignore lamp posts and benches). 150 cm lets two wheelchairs pass each other.' },
    ],
  },
  {
    type: 'DOOR',
    label: 'Door',
    icon: 'door_front',
    markerColor: '#8B6A00',
    environments: ['OUTDOOR', 'INDOOR'],
    issues: [
      { key: 'MISSING',               label: 'Missing' },
      { key: 'TOO_NARROW',            label: 'Too Narrow' },
      { key: 'HIGH_THRESHOLD',        label: 'High Threshold' },
      { key: 'STEP_AT_ENTRANCE',      label: 'Step at Entrance' },
      { key: 'NO_LEVER_HANDLE',       label: 'No Lever Handle' },
      { key: 'HEAVY_DOOR',            label: 'Heavy Door' },
      { key: 'NO_AUTOMATIC_DOOR',     label: 'No Automatic Door' },
      { key: 'NO_GLASS_MARKING',      label: 'No Glass Marking' },
      { key: 'HANDLE_HEIGHT_INVALID', label: 'Handle Height Invalid' },
      { key: 'INTERCOM_INACCESSIBLE', label: 'Intercom Inaccessible' },
    ],
    measurements: [
      { key: 'width_cm',            label: 'Width',            unit: 'cm', accessible_min: 90,  description: 'Clear opening when the door is fully open. 90 cm fits a standard wheelchair.' },
      { key: 'threshold_height_cm', label: 'Threshold Height', unit: 'cm', accessible_max: 0.6, description: 'The lip at the bottom of the doorway. Above ~0.6 cm catches small front wheels on a wheelchair.' },
    ],
  },
  {
    type: 'STAIR',
    label: 'Stair',
    icon: 'stairs',
    markerColor: '#B65900',
    environments: ['OUTDOOR', 'INDOOR'],
    issues: [
      { key: 'MISSING',              label: 'Missing' },
      { key: 'TOO_NARROW',           label: 'Too Narrow' },
      { key: 'MISSING_HANDRAIL',     label: 'Missing Handrail' },
      { key: 'NO_LANDING',           label: 'No Landing' },
      { key: 'SLIPPERY_SURFACE',     label: 'Slippery Surface' },
      { key: 'RISER_TOO_HIGH',       label: 'Riser Too High' },
      { key: 'TREAD_TOO_SHALLOW',    label: 'Tread Too Shallow' },
      { key: 'NO_ANTI_SLIP',         label: 'No Anti-Slip' },
      { key: 'OPEN_RISERS',          label: 'Open Risers' },
      { key: 'IRREGULAR_STEPS',      label: 'Irregular Steps' },
      { key: 'MISSING_NOSING_STRIP', label: 'Missing Nosing Strip' },
    ],
    measurements: [
      { key: 'riser_cm', label: 'Riser Height', unit: 'cm', accessible_max: 16, description: 'Height of each step. Above 16 cm gets exhausting and risky for people with mobility limits.' },
      { key: 'tread_cm', label: 'Tread Depth',  unit: 'cm', accessible_min: 27, description: 'Front-to-back depth of each step. Below 27 cm is too narrow to land a whole foot safely.' },
    ],
  },
  {
    type: 'PEDESTRIAN_CROSSING',
    label: 'Pedestrian Crossing',
    icon: 'traffic',
    markerColor: '#1565C0',
    environments: ['OUTDOOR'],
    issues: [
      { key: 'MISSING',              label: 'Missing' },
      { key: 'BLOCKED',              label: 'Blocked' },
      { key: 'NO_TACTILE_PAVING',    label: 'No Tactile Paving' },
      { key: 'NO_AUDIO_SIGNAL',      label: 'No Audio Signal' },
      { key: 'SIGNAL_TOO_SHORT',     label: 'Signal Too Short' },
      { key: 'NO_DROPPED_CURB',      label: 'No Dropped Curb' },
      { key: 'FADED_MARKINGS',       label: 'Faded Markings' },
      { key: 'NO_PEDESTRIAN_REFUGE', label: 'No Pedestrian Refuge' },
    ],
    measurements: [
      { key: 'signal_duration_sec',    label: 'Signal Duration',     unit: 's',  accessible_min: 15,  description: 'How long the walk signal stays on. 15 s is enough for a slow walker to cross a typical street.' },
      { key: 'crossing_width_cm',      label: 'Crossing Width',      unit: 'cm', accessible_min: 300, description: 'Width of the marked crossing. 300 cm fits two wheelchairs passing in opposite directions.' },
      { key: 'dropped_curb_height_cm', label: 'Dropped Curb Height', unit: 'cm', accessible_max: 1.3, description: 'Step from the crossing onto the sidewalk. Above ~1.3 cm catches wheels and trips canes.' },
    ],
  },
  {
    type: 'CURB_RAMP',
    label: 'Curb Ramp',
    icon: 'accessible',
    markerColor: '#6A4C00',
    environments: ['OUTDOOR'],
    issues: [
      { key: 'MISSING',            label: 'Missing' },
      { key: 'TOO_STEEP',          label: 'Too Steep' },
      { key: 'TOO_NARROW',         label: 'Too Narrow' },
      { key: 'NO_TACTILE_WARNING', label: 'No Tactile Warning' },
      { key: 'RAISED_LIP',         label: 'Raised Lip' },
    ],
    measurements: [
      { key: 'slope_percent', label: 'Slope',      unit: '%',  accessible_max: 8,   description: 'Steepness of the curb ramp. 8% is the comfortable maximum for a person pushing themself in a wheelchair.' },
      { key: 'width_cm',      label: 'Width',      unit: 'cm', accessible_min: 120, description: 'Width of the ramp surface. 120 cm matches the wheelchair-friendly minimum.' },
      { key: 'lip_height_cm', label: 'Lip Height', unit: 'cm', accessible_max: 1.3, description: 'Bump where the ramp meets the road. Above 1.3 cm jolts wheels and trips canes.' },
    ],
  },
  {
    type: 'WASHROOM',
    label: 'Washroom',
    icon: 'wc',
    markerColor: '#00838F',
    environments: ['INDOOR'],
    issues: [
      { key: 'MISSING',                    label: 'Missing' },
      { key: 'TOO_NARROW',                 label: 'Too Narrow' },
      { key: 'NO_GRAB_BAR',                label: 'No Grab Bar' },
      { key: 'HIGH_THRESHOLD',             label: 'High Threshold' },
      { key: 'NO_ACCESSIBLE_STALL',        label: 'No Accessible Stall' },
      { key: 'INSUFFICIENT_TURNING_SPACE', label: 'Insufficient Turning Space' },
      { key: 'SINK_TOO_HIGH',              label: 'Sink Too High' },
      { key: 'NO_EMERGENCY_BUTTON',        label: 'No Emergency Button' },
    ],
    measurements: [
      { key: 'door_width_cm',      label: 'Door Width',      unit: 'cm', accessible_min: 90,  description: 'Clear opening when the door is fully open. 90 cm fits a standard wheelchair.' },
      { key: 'turning_space_cm',   label: 'Turning Space',   unit: 'cm', accessible_min: 150, description: 'Open circle of clear floor space. 150 cm lets a wheelchair turn fully around.' },
      { key: 'sink_height_cm',     label: 'Sink Height',     unit: 'cm', accessible_max: 85,  description: 'Top of the sink rim from the floor. Below 85 cm lets a seated user reach the tap.' },
      { key: 'grab_bar_height_cm', label: 'Grab Bar Height', unit: 'cm', accessible_min: 70,  description: 'Height of the grab bars from the floor. 70 cm or higher lines up with most transfer holds.' },
    ],
  },
  {
    type: 'ROOM_SIGN',
    label: 'Room Sign',
    icon: 'meeting_room',
    markerColor: '#5E35B1',
    environments: ['INDOOR'],
    issues: [
      { key: 'MISSING',               label: 'Missing' },
      { key: 'NO_BRAILLE',            label: 'No Braille' },
      { key: 'INSUFFICIENT_LIGHTING', label: 'Insufficient Lighting' },
      { key: 'LOW_CONTRAST',          label: 'Low Contrast' },
      { key: 'TEXT_TOO_SMALL',        label: 'Text Too Small' },
      { key: 'INVALID_HEIGHT',        label: 'Invalid Height' },
    ],
    measurements: [
      { key: 'mounting_height_cm', label: 'Mounting Height', unit: 'cm', accessible_min: 120, accessible_max: 160, description: 'Center of the sign from the floor. 120-160 cm aligns with eye level for both standing and seated users.' },
      { key: 'text_height_mm',     label: 'Text Height',     unit: 'mm', accessible_min: 15,                       description: 'Height of the letterforms. Below 15 mm is hard to read from door distance.' },
    ],
  },
]

export const OBJECT_TYPE_MAP = Object.fromEntries(OBJECT_TYPES.map(t => [t.type, t]))

/**
 * Return a localized copy of an object-type config. Pass the `t` function from
 * `useTranslation()`. Keys live under `object.<TYPE>.*` in the locale JSON;
 * missing keys fall back to the English label/description in this file.
 *
 * Strings translated: object label, issue labels, measurement labels,
 * measurement descriptions (the long-form prose shown in ObjectTutorialModal).
 */
export function localizeObjectType(t, cfg) {
  if (!cfg) return cfg
  return {
    ...cfg,
    label: t(`object.${cfg.type}.label`, { defaultValue: cfg.label }),
    issues: cfg.issues.map((i) => ({
      ...i,
      label: t(`object.${cfg.type}.issue.${i.key}`, { defaultValue: i.label }),
    })),
    measurements: cfg.measurements.map((m) => ({
      ...m,
      label: t(`object.${cfg.type}.measurement.${m.key}.label`, { defaultValue: m.label }),
      description: m.description
        ? t(`object.${cfg.type}.measurement.${m.key}.description`, { defaultValue: m.description })
        : m.description,
    })),
  }
}
