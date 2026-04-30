// Client-side password rules mirror the backend regex in
// RegisteredUserService.java: minimum 8 chars, and at least one uppercase,
// lowercase, digit, and special char from @#$%^&+=!
export const SPECIAL_CHARS = '@#$%^&+=!'

export const PASSWORD_RULES = [
  {
    id: 'length',
    label: 'At least 8 characters',
    message: 'Password must be at least 8 characters long.',
    test: (v) => v.length >= 8,
  },
  {
    id: 'uppercase',
    label: 'At least one uppercase letter',
    message: 'Password must contain at least one uppercase letter.',
    test: (v) => /[A-Z]/.test(v),
  },
  {
    id: 'lowercase',
    label: 'At least one lowercase letter',
    message: 'Password must contain at least one lowercase letter.',
    test: (v) => /[a-z]/.test(v),
  },
  {
    id: 'digit',
    label: 'At least one number',
    message: 'Password must contain at least one number.',
    test: (v) => /[0-9]/.test(v),
  },
  {
    id: 'special',
    label: `At least one special character (${SPECIAL_CHARS})`,
    message: `Password must contain at least one special character (${SPECIAL_CHARS}).`,
    test: (v) => new RegExp(`[${SPECIAL_CHARS.replace(/[-\\^\]]/g, '\\$&')}]`).test(v),
  },
]

export function validatePassword(value = '') {
  const results = PASSWORD_RULES.map((rule) => ({
    id: rule.id,
    label: rule.label,
    message: rule.message,
    passed: rule.test(value),
  }))
  return {
    results,
    failures: results.filter((r) => !r.passed),
    isValid: results.every((r) => r.passed),
  }
}
