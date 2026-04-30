import { describe, it, expect } from 'vitest'
import { validatePassword, PASSWORD_RULES } from './passwordValidation.js'

describe('validatePassword', () => {
  it('fails all rules for an empty password', () => {
    const { isValid, failures } = validatePassword('')
    expect(isValid).toBe(false)
    expect(failures.map((r) => r.id).sort()).toEqual(
      PASSWORD_RULES.map((r) => r.id).sort(),
    )
  })

  it('reports length failure for short inputs', () => {
    const { results } = validatePassword('Aa1!')
    const length = results.find((r) => r.id === 'length')
    expect(length.passed).toBe(false)
    expect(length.message).toMatch(/at least 8 characters/i)
  })

  it('reports uppercase failure when only lowercase', () => {
    const { results } = validatePassword('abcdefg1!')
    expect(results.find((r) => r.id === 'uppercase').passed).toBe(false)
  })

  it('reports lowercase failure when only uppercase', () => {
    const { results } = validatePassword('ABCDEFG1!')
    expect(results.find((r) => r.id === 'lowercase').passed).toBe(false)
  })

  it('reports digit failure when no numbers', () => {
    const { results } = validatePassword('Abcdefgh!')
    expect(results.find((r) => r.id === 'digit').passed).toBe(false)
  })

  it('reports special character failure when no special', () => {
    const { results } = validatePassword('Abcdefg1')
    const special = results.find((r) => r.id === 'special')
    expect(special.passed).toBe(false)
    expect(special.message).toMatch(/special character/i)
  })

  it('passes for a strong password meeting all rules', () => {
    const { isValid, failures } = validatePassword('Secret12!')
    expect(isValid).toBe(true)
    expect(failures).toEqual([])
  })

  it('accepts each allowed special character', () => {
    for (const ch of '@#$%^&+=!') {
      const { isValid } = validatePassword(`Secret1${ch}`)
      expect(isValid, `expected '${ch}' to satisfy special rule`).toBe(true)
    }
  })
})
