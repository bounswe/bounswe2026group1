// Client-side password rules mirror the backend regex in
// RegisteredUserService.java: minimum 8 chars, and at least one uppercase,
// lowercase, digit, and special char from @#$%^&+=!
//
// Labels and messages live in the locale JSON under `passwordRules.<id>.*`
// and are resolved by Signup at render time so the checklist follows the
// active language (issue #546). The util stays pure — no React imports —
// so it can be reused outside React (tests, backend mirror checks, etc.).
export const SPECIAL_CHARS = '@#$%^&+=!'

export const PASSWORD_RULES = [
  { id: 'length',    test: (v) => v.length >= 8 },
  { id: 'uppercase', test: (v) => /[A-Z]/.test(v) },
  { id: 'lowercase', test: (v) => /[a-z]/.test(v) },
  { id: 'digit',     test: (v) => /[0-9]/.test(v) },
  {
    id: 'special',
    test: (v) => new RegExp(`[${SPECIAL_CHARS.replace(/[-\\^\]]/g, '\\$&')}]`).test(v),
  },
]

export function validatePassword(value = '') {
  const results = PASSWORD_RULES.map((rule) => ({
    id: rule.id,
    passed: rule.test(value),
  }))
  return {
    results,
    failures: results.filter((r) => !r.passed),
    isValid: results.every((r) => r.passed),
  }
}
