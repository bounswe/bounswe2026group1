import {
  buildReportJsonLd,
  reportJsonLdString,
  OBJECT_TYPE_TO_FEATURE,
  ISSUE_TYPE_TO_HAZARD,
} from './schemaOrg.js'
import { OBJECT_TYPES } from './objectTypeConfig.js'

describe('schemaOrg.buildReportJsonLd', () => {
  const baseReport = {
    id: 42,
    title: 'Broken Curb Ramp',
    description: 'The curb ramp at the corner is too steep for a wheelchair.',
    latitude: 41.0824,
    longitude: 29.0509,
    objects: [
      {
        objectType: 'CURB_RAMP',
        issues: ['TOO_STEEP', 'NO_TACTILE_WARNING'],
      },
    ],
  }

  test('returns null when report is missing', () => {
    expect(buildReportJsonLd(null)).toBeNull()
    expect(buildReportJsonLd(undefined)).toBeNull()
  })

  test('returns null when report has no id', () => {
    expect(buildReportJsonLd({ title: 'No id' })).toBeNull()
  })

  test('produces a valid Place node with the expected core fields', () => {
    const ld = buildReportJsonLd(baseReport)
    expect(ld['@context']).toBe('https://schema.org')
    expect(ld['@type']).toBe('Place')
    expect(ld['@id']).toBe('mapcess:report:42')
    expect(ld.name).toBe('Broken Curb Ramp')
    expect(ld.description).toBe(baseReport.description)
  })

  test('emits geo coordinates as numbers', () => {
    const ld = buildReportJsonLd(baseReport)
    expect(ld.geo).toEqual({
      '@type': 'GeoCoordinates',
      latitude: 41.0824,
      longitude: 29.0509,
    })
  })

  test('coerces stringly-typed coordinates to numbers', () => {
    const ld = buildReportJsonLd({ ...baseReport, latitude: '41.5', longitude: '29.0' })
    expect(ld.geo.latitude).toBe(41.5)
    expect(ld.geo.longitude).toBe(29.0)
  })

  test('omits geo when coordinates are missing or invalid', () => {
    const noCoords = buildReportJsonLd({ ...baseReport, latitude: null, longitude: null })
    expect(noCoords.geo).toBeUndefined()
    const empty = buildReportJsonLd({ ...baseReport, latitude: '', longitude: '' })
    expect(empty.geo).toBeUndefined()
    const garbage = buildReportJsonLd({ ...baseReport, latitude: 'abc', longitude: 'xyz' })
    expect(garbage.geo).toBeUndefined()
  })

  test('maps ObjectType to accessibilityFeature and IssueType to accessibilityHazard', () => {
    const ld = buildReportJsonLd(baseReport)
    expect(ld.accessibilityFeature).toEqual(['curbRamp'])
    expect(ld.accessibilityHazard).toEqual(['steepGradient', 'noTactileWarning'])
  })

  test('deduplicates feature and hazard tokens across multiple objects', () => {
    const ld = buildReportJsonLd({
      ...baseReport,
      objects: [
        { objectType: 'RAMP', issues: ['TOO_STEEP'] },
        { objectType: 'RAMP', issues: ['TOO_STEEP', 'TOO_NARROW'] },
      ],
    })
    expect(ld.accessibilityFeature).toEqual(['ramp'])
    expect(ld.accessibilityHazard).toEqual(['steepGradient', 'tooNarrow'])
  })

  test('drops unknown object types and issues silently', () => {
    const ld = buildReportJsonLd({
      ...baseReport,
      objects: [
        { objectType: 'UNKNOWN_OBJECT', issues: ['UNKNOWN_ISSUE', 'TOO_STEEP'] },
      ],
    })
    expect(ld.accessibilityFeature).toBeUndefined()
    expect(ld.accessibilityHazard).toEqual(['steepGradient'])
  })

  test('omits accessibilityFeature/Hazard arrays when empty', () => {
    const ld = buildReportJsonLd({ ...baseReport, objects: [] })
    expect(ld.accessibilityFeature).toBeUndefined()
    expect(ld.accessibilityHazard).toBeUndefined()
  })

  test('attaches url when one is supplied', () => {
    const ld = buildReportJsonLd(baseReport, { url: 'https://mapcess.live/?report=42' })
    expect(ld.url).toBe('https://mapcess.live/?report=42')
  })
})

describe('schemaOrg.reportJsonLdString', () => {
  test('returns empty string when report is unrenderable', () => {
    expect(reportJsonLdString(null)).toBe('')
    expect(reportJsonLdString({})).toBe('')
  })

  test('serializes to valid JSON that round-trips', () => {
    const report = {
      id: 7,
      title: 'Missing Ramp',
      latitude: 1,
      longitude: 2,
      objects: [{ objectType: 'RAMP', issues: ['MISSING'] }],
    }
    const str = reportJsonLdString(report)
    // Escaped `</` should not appear as a literal closing tag inside a script.
    expect(str.includes('</')).toBe(false)
    const parsed = JSON.parse(str.replace(/\\u003c/g, '<'))
    expect(parsed['@type']).toBe('Place')
    expect(parsed.accessibilityFeature).toEqual(['ramp'])
    expect(parsed.accessibilityHazard).toEqual(['missingFeature'])
  })

  test('escapes inline </script> sequences in the description', () => {
    const report = {
      id: 9,
      title: 'Hostile Description',
      description: '</script><img src=x onerror=alert(1)>',
      objects: [],
    }
    const str = reportJsonLdString(report)
    expect(str.toLowerCase().includes('</script')).toBe(false)
    // The escaped form should still parse back to the original text.
    const parsed = JSON.parse(str.replace(/\\u003c/g, '<'))
    expect(parsed.description).toBe('</script><img src=x onerror=alert(1)>')
  })
})

describe('schemaOrg coverage of the project taxonomy', () => {
  test('every ObjectType has a feature mapping', () => {
    for (const t of OBJECT_TYPES) {
      expect(OBJECT_TYPE_TO_FEATURE[t.type], `${t.type} missing feature mapping`).toBeTruthy()
    }
  })

  test('every IssueType across the catalog has a hazard mapping', () => {
    for (const t of OBJECT_TYPES) {
      for (const issue of t.issues) {
        expect(
          ISSUE_TYPE_TO_HAZARD[issue.key],
          `${t.type}.${issue.key} missing hazard mapping`,
        ).toBeTruthy()
      }
    }
  })
})
