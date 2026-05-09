import { OBJECT_TYPES, OBJECT_TYPE_MAP } from './objectTypeConfig.js'

describe('OBJECT_TYPES catalog', () => {
  test('contains exactly nine object types', () => {
    expect(OBJECT_TYPES).toHaveLength(9)
  })

  test('every entry has the required fields', () => {
    for (const t of OBJECT_TYPES) {
      expect(t.type, `${t.type ?? 'unknown'} must have a type`).toBeTruthy()
      expect(t.label, `${t.type} must have a label`).toBeTruthy()
      expect(t.icon, `${t.type} must have an icon`).toBeTruthy()
      expect(t.markerColor, `${t.type} must have a markerColor`).toMatch(/^#[0-9A-Fa-f]{6}$/)
      expect(Array.isArray(t.environments), `${t.type} environments must be an array`).toBe(true)
      expect(t.environments.length, `${t.type} must support at least one environment`).toBeGreaterThan(0)
      expect(Array.isArray(t.issues), `${t.type} issues must be an array`).toBe(true)
      expect(t.issues.length, `${t.type} must have at least one issue`).toBeGreaterThan(0)
      expect(Array.isArray(t.measurements), `${t.type} measurements must be an array`).toBe(true)
    }
  })

  test('environments only contain INDOOR and/or OUTDOOR', () => {
    const allowed = new Set(['INDOOR', 'OUTDOOR'])
    for (const t of OBJECT_TYPES) {
      for (const env of t.environments) {
        expect(allowed.has(env), `${t.type} has invalid environment ${env}`).toBe(true)
      }
    }
  })

  test('object type values are unique', () => {
    const types = OBJECT_TYPES.map(t => t.type)
    expect(new Set(types).size).toBe(types.length)
  })

  test('every object type allows the MISSING issue', () => {
    for (const t of OBJECT_TYPES) {
      const keys = t.issues.map(i => i.key)
      expect(keys, `${t.type} should include MISSING`).toContain('MISSING')
    }
  })

  test('issue keys are unique within each object type', () => {
    for (const t of OBJECT_TYPES) {
      const keys = t.issues.map(i => i.key)
      expect(new Set(keys).size, `${t.type} has duplicate issue keys`).toBe(keys.length)
    }
  })

  test('measurement keys are unique within each object type', () => {
    for (const t of OBJECT_TYPES) {
      const keys = t.measurements.map(m => m.key)
      expect(new Set(keys).size, `${t.type} has duplicate measurement keys`).toBe(keys.length)
    }
  })

  test('environment matrix matches the backend taxonomy', () => {
    const expected = {
      RAMP:                ['OUTDOOR', 'INDOOR'],
      ELEVATOR:            ['OUTDOOR', 'INDOOR'],
      SIDEWALK:            ['OUTDOOR'],
      DOOR:                ['OUTDOOR', 'INDOOR'],
      STAIR:               ['OUTDOOR', 'INDOOR'],
      PEDESTRIAN_CROSSING: ['OUTDOOR'],
      CURB_RAMP:           ['OUTDOOR'],
      WASHROOM:            ['INDOOR'],
      ROOM_SIGN:           ['INDOOR'],
    }
    for (const [type, envs] of Object.entries(expected)) {
      expect(OBJECT_TYPE_MAP[type]?.environments).toEqual(envs)
    }
  })

  test('OBJECT_TYPE_MAP exposes every object type by its key', () => {
    for (const t of OBJECT_TYPES) {
      expect(OBJECT_TYPE_MAP[t.type]).toBe(t)
    }
    expect(Object.keys(OBJECT_TYPE_MAP)).toHaveLength(OBJECT_TYPES.length)
  })
})
