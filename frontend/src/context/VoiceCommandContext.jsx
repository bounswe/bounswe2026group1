import { createContext, useCallback, useContext, useMemo, useState } from 'react'

/**
 * VoiceCommandContext (1.1.3.8)
 *
 * Tiny app-level state to bridge the Navbar mic toggle (where the user opts
 * into a listening session) with the VoiceCommandRunner inside Home (where
 * the recognized commands actually fire against the map/route/report state).
 *
 * State:
 *   - active:    is a listening session currently on?
 *   - lastEvent: { kind: 'transcript' | 'recognized' | 'unrecognized' | 'error', text }
 *                last announcement-worthy event, consumed by the ARIA live region.
 */

const VoiceCommandContext = createContext(null)

export function VoiceCommandProvider({ children }) {
  const [active, setActive] = useState(false)
  const [lastEvent, setLastEvent] = useState(null)

  const toggle = useCallback(() => setActive((v) => !v), [])
  const announce = useCallback((kind, text) => setLastEvent({ kind, text, t: Date.now() }), [])

  const value = useMemo(
    () => ({ active, setActive, toggle, lastEvent, announce }),
    [active, toggle, lastEvent, announce],
  )

  return <VoiceCommandContext.Provider value={value}>{children}</VoiceCommandContext.Provider>
}

// Inert fallback so components rendered outside the provider (e.g. existing
// Vitest renders that mount Navbar directly) keep working — voice features
// are opt-in and shouldn't break unrelated trees.
const INERT = {
  active: false,
  setActive: () => {},
  toggle: () => {},
  lastEvent: null,
  announce: () => {},
}

export function useVoiceCommandContext() {
  return useContext(VoiceCommandContext) ?? INERT
}
