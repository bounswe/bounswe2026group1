import { useVoiceCommandContext } from '../context/VoiceCommandContext.jsx'
import { useCurrentUser } from '../hooks/useCurrentUser.js'

/**
 * Mic toggle button for the Navbar (1.1.3.8).
 *
 * Visible only when the authenticated user has opted in via Settings
 * (`voiceCommandsEnabled` on the profile DTO). When the browser doesn't
 * support SpeechRecognition the button is hidden — we surface the
 * unsupported-browser notice next to the toggle in Settings instead so the
 * navbar isn't permanently empty for ~5% of users on Firefox/Safari without
 * the polyfill.
 */
const SR_SUPPORTED = typeof window !== 'undefined' &&
  Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)

function VoiceCommandMicButton() {
  const { data: user } = useCurrentUser()
  const { active, toggle } = useVoiceCommandContext()

  if (!user?.voiceCommandsEnabled) return null
  if (!SR_SUPPORTED) return null

  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={active}
      aria-label={active ? 'Stop voice commands' : 'Start voice commands'}
      title={active ? 'Listening… click to stop' : 'Click to start voice commands'}
      className={`hidden sm:flex shrink-0 w-10 h-10 rounded-full items-center justify-center transition-colors cursor-pointer ${
        active
          ? 'bg-error/15 text-error motion-safe:animate-pulse'
          : 'hover:bg-surface-container text-on-surface-variant'
      }`}
    >
      <span className="material-symbols-outlined" style={{ fontVariationSettings: active ? "'FILL' 1" : "'FILL' 0" }}>
        {active ? 'mic' : 'mic_none'}
      </span>
    </button>
  )
}

export default VoiceCommandMicButton
