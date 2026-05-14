import { useVoiceCommandContext } from '../context/VoiceCommandContext.jsx'

/**
 * Visually-hidden polite live region (1.1.3.8). Mounts once near the root so
 * screen-readers announce every voice-command outcome — what was heard, what
 * fired, and what was unrecognized — without competing with the visible UI.
 *
 * Polite (not assertive) so it doesn't interrupt ongoing readouts; voice
 * feedback is supplementary, not safety-critical.
 */
function VoiceCommandLiveRegion() {
  const { lastEvent } = useVoiceCommandContext()
  const text = lastEvent
    ? lastEvent.kind === 'recognized'
        ? lastEvent.text
      : lastEvent.kind === 'unrecognized'
          ? `Command not recognized: ${lastEvent.text}`
        : lastEvent.kind === 'transcript'
            ? `Heard: ${lastEvent.text}`
          : lastEvent.kind === 'error'
              ? lastEvent.text
            : ''
    : ''
  return (
    <div
      aria-live="polite"
      aria-atomic="true"
      className="sr-only"
      // The `key` swap forces the live region to re-announce identical text
      // (e.g. repeated "Zoom in") which screen-readers otherwise dedupe.
      key={lastEvent?.t ?? 0}
    >
      {text}
    </div>
  )
}

export default VoiceCommandLiveRegion
