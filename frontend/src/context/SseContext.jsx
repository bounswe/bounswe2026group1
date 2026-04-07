import { createContext, useContext, useState } from 'react'

const SseContext = createContext('disconnected')

export function SseProvider({ children }) {
  const [status, setStatus] = useState('disconnected')

  return (
    <SseContext.Provider value={{ status, setStatus }}>
      {children}
    </SseContext.Provider>
  )
}

/** Returns the raw SSE status string: 'connected' | 'reconnecting' | 'disconnected' */
export function useSseStatus() {
  return useContext(SseContext).status
}

/** Internal use only — called by useSseSync to update the shared status. */
export function useSseStatusSetter() {
  return useContext(SseContext).setStatus
}
