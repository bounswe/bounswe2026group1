import { createContext, useContext, useEffect, useState, useCallback } from 'react'

// Mirrors mobile's ThemeService (mobile/lib/services/theme_service.dart):
// three modes, persisted across reloads, and 'system' tracks OS preference
// live via matchMedia. Resolved brightness is reflected on <html> as both a
// `.dark` class (legacy callers) and `data-theme="dark"` attribute, so the
// CSS variable overrides in index.css cascade through every Tailwind utility.

const STORAGE_KEY = 'theme_mode'
const VALID_MODES = new Set(['system', 'light', 'dark'])

const ThemeContext = createContext(null)

function readStoredMode() {
  if (typeof window === 'undefined') return 'system'
  const raw = window.localStorage.getItem(STORAGE_KEY)
  return VALID_MODES.has(raw) ? raw : 'system'
}

function readSystemDark() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyBrightness(brightness) {
  const root = document.documentElement
  root.dataset.theme = brightness
  root.classList.toggle('dark', brightness === 'dark')
}

export function ThemeProvider({ children }) {
  const [mode, setModeState] = useState(readStoredMode)
  // System preference lives in its own state so resolved brightness is a
  // pure derivation. Avoids setState-inside-effect.
  const [systemDark, setSystemDark] = useState(readSystemDark)

  const resolved = mode === 'system' ? (systemDark ? 'dark' : 'light') : mode

  // Subscribe once; update systemDark whenever the OS preference flips.
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    function onChange() { setSystemDark(mql.matches) }
    mql.addEventListener?.('change', onChange)
    return () => mql.removeEventListener?.('change', onChange)
  }, [])

  // Push resolved brightness onto <html> whenever it changes.
  useEffect(() => {
    applyBrightness(resolved)
  }, [resolved])

  const setMode = useCallback((next) => {
    if (!VALID_MODES.has(next)) return
    setModeState(next)
    try { window.localStorage.setItem(STORAGE_KEY, next) } catch { /* quota / private mode */ }
  }, [])

  return (
    <ThemeContext.Provider value={{ mode, resolved, setMode }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within a ThemeProvider')
  return ctx
}
