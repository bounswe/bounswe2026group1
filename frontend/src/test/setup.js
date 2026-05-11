import '@testing-library/jest-dom'
// Bootstrap i18next once for the whole test run. main.jsx does this in app
// code; tests don't import main.jsx, so without this t('foo') would return
// the raw key and any assertion on a translated string would fail.
import '../i18n/index.js'

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
