import { describe, it, expect } from 'vitest'
import { parseVoiceCommand } from './voiceCommandParser.js'

describe('parseVoiceCommand', () => {
  it('returns UNKNOWN for empty or whitespace input', () => {
    expect(parseVoiceCommand('')).toEqual({ type: 'UNKNOWN', raw: '' })
    expect(parseVoiceCommand('   ')).toEqual({ type: 'UNKNOWN', raw: '   ' })
    expect(parseVoiceCommand(null).type).toBe('UNKNOWN')
    expect(parseVoiceCommand(undefined).type).toBe('UNKNOWN')
  })

  it('parses map control verbs', () => {
    expect(parseVoiceCommand('zoom in')).toEqual({ type: 'ZOOM_IN' })
    expect(parseVoiceCommand('Zoom In.')).toEqual({ type: 'ZOOM_IN' })
    expect(parseVoiceCommand('zoom out')).toEqual({ type: 'ZOOM_OUT' })
    expect(parseVoiceCommand('my location')).toEqual({ type: 'LOCATE_ME' })
    expect(parseVoiceCommand('locate me')).toEqual({ type: 'LOCATE_ME' })
    expect(parseVoiceCommand('where am I?')).toEqual({ type: 'LOCATE_ME' })
  })

  it('parses search variants', () => {
    expect(parseVoiceCommand('find pharmacy')).toEqual({ type: 'SEARCH', query: 'pharmacy' })
    expect(parseVoiceCommand('search pharmacy')).toEqual({ type: 'SEARCH', query: 'pharmacy' })
    expect(parseVoiceCommand('search for accessible cafes')).toEqual({
      type: 'SEARCH', query: 'accessible cafes',
    })
  })

  it('parses navigation variants', () => {
    expect(parseVoiceCommand('navigate to Taksim')).toEqual({ type: 'NAVIGATE', dest: 'taksim' })
    expect(parseVoiceCommand('go to Boğaziçi University')).toEqual({
      type: 'NAVIGATE', dest: 'boğaziçi university',
    })
  })

  it('parses report variants', () => {
    expect(parseVoiceCommand('report here')).toEqual({ type: 'REPORT', category: null })
    expect(parseVoiceCommand('report obstacle')).toEqual({ type: 'REPORT', category: null })
    expect(parseVoiceCommand('report ramp here')).toEqual({ type: 'REPORT', category: 'ramp' })
    expect(parseVoiceCommand('report missing curb here')).toEqual({
      type: 'REPORT', category: 'missing curb',
    })
  })

  it('returns UNKNOWN for unmatched phrases', () => {
    const r = parseVoiceCommand('asdfasdf')
    expect(r.type).toBe('UNKNOWN')
    expect(r.raw).toBe('asdfasdf')
  })

  it('normalises punctuation and whitespace', () => {
    expect(parseVoiceCommand('  zoom in.  ')).toEqual({ type: 'ZOOM_IN' })
    expect(parseVoiceCommand('Find\tpharmacy')).toEqual({ type: 'SEARCH', query: 'pharmacy' })
  })
})
