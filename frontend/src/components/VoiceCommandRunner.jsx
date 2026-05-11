import { useCallback, useEffect } from 'react'
import { useMap } from 'react-leaflet'
import { useSpeechRecognition } from '../hooks/useSpeechRecognition.js'
import { useVoiceCommandContext } from '../context/VoiceCommandContext.jsx'
import { parseVoiceCommand } from '../utils/voiceCommandParser.js'

/**
 * VoiceCommandRunner (1.1.3.8)
 *
 * Lives inside a Leaflet MapContainer so it can drive the map via useMap().
 * Reads `active` from VoiceCommandContext — when on, mounts the recognition
 * hook, parses every final transcript through parseVoiceCommand, and routes
 * the result to the appropriate Home-page setter.
 *
 * Reuses Home's existing actions so a voice command is exactly equivalent to
 * the click path; no duplicate logic.
 *
 * Renders nothing.
 */
function VoiceCommandRunner({
  userLocation,
  onSearch,
  onNavigate,
  onReport,
  onLocationError,
}) {
  const map = useMap()
  const { active, setActive, announce } = useVoiceCommandContext()

  const handleTranscript = useCallback(
    (transcript) => {
      announce('transcript', transcript)
      const cmd = parseVoiceCommand(transcript)
      switch (cmd.type) {
        case 'ZOOM_IN':
          map.zoomIn()
          announce('recognized', 'Zoom in')
          break
        case 'ZOOM_OUT':
          map.zoomOut()
          announce('recognized', 'Zoom out')
          break
        case 'LOCATE_ME':
          if (userLocation) {
            map.flyTo([userLocation.lat, userLocation.lng], 16)
            announce('recognized', 'Centering on your location')
          } else {
            onLocationError?.()
            announce('error', 'Location unavailable')
          }
          break
        case 'SEARCH':
          onSearch?.(cmd.query)
          announce('recognized', `Searching for ${cmd.query}`)
          break
        case 'NAVIGATE':
          onNavigate?.(cmd.dest)
          announce('recognized', `Planning a route to ${cmd.dest}`)
          break
        case 'REPORT':
          onReport?.(cmd.category)
          announce('recognized', cmd.category ? `Reporting ${cmd.category}` : 'Opening report panel')
          break
        default:
          announce('unrecognized', transcript)
      }
    },
    [map, userLocation, onSearch, onNavigate, onReport, onLocationError, announce],
  )

  const { supported, start, stop, error } = useSpeechRecognition({
    onTranscript: handleTranscript,
  })

  // Unsupported browser: force `active` off so the mic toggle button reflects
  // reality. The Navbar shows its own "not supported" tooltip via the same
  // capability check.
  useEffect(() => {
    if (!supported && active) {
      setActive(false)
      announce('error', 'Voice commands are not supported in this browser.')
    }
  }, [supported, active, setActive, announce])

  useEffect(() => {
    if (active && supported) start()
    else stop()
    return () => stop()
  }, [active, supported, start, stop])

  // Surface recognizer errors (permission denied etc.) into the live region.
  useEffect(() => {
    if (error) announce('error', `Microphone error: ${error}`)
  }, [error, announce])

  return null
}

export default VoiceCommandRunner
