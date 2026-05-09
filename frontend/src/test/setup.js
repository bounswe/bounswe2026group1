import '@testing-library/jest-dom'

// jsdom doesn't implement matchMedia. ThemeContext queries it on mount to
// resolve `system` against OS preference; provide a no-op stub that reports
// "light" so tests render in light mode by default.
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })
}
