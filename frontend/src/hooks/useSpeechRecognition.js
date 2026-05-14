import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Thin React wrapper around the browser SpeechRecognition API (1.1.3.8).
 *
 * Feature-detects window.SpeechRecognition / window.webkitSpeechRecognition.
 * When unsupported, `supported` is false and start()/stop() are no-ops so the
 * caller can render an unsupported-browser fallback without crashing.
 *
 * Behaviour:
 *   - `start()` requests mic permission (browser prompts on first use) and
 *     begins continuous recognition; the hook re-arms the recognizer if it
 *     ends unexpectedly while `listening` is still true.
 *   - Each *final* transcript fires `onTranscript(transcript)`. Interim
 *     transcripts are ignored — the parser is intent-based, not realtime.
 *   - `stop()` halts recognition and clears any pending re-arm.
 *
 * Errors surface as `error` (string code), the ARIA live region is the
 * caller's responsibility (see VoiceCommandLiveRegion).
 */
export function useSpeechRecognition({ onTranscript, lang = 'en-US' } = {}) {
  const Ctor = typeof window !== 'undefined'
    ? (window.SpeechRecognition || window.webkitSpeechRecognition)
    : null
  const supported = Boolean(Ctor)

  const [listening, setListening] = useState(false)
  const [error, setError] = useState(null)
  const recognizerRef = useRef(null)
  // shouldKeepListening mirrors `listening` in a ref so the SpeechRecognition
  // `onend` handler — which fires after every quiet pause — can decide
  // whether to re-arm without closing over stale state.
  const shouldKeepListeningRef = useRef(false)
  // Hold the latest onTranscript in a ref so callers can change it without
  // having to recreate start()/stop() — the SpeechRecognition instance keeps
  // a reference to the handler set at creation, and we don't want a fresh
  // closure on every render to lose in-flight results.
  const onTranscriptRef = useRef(onTranscript)
  useEffect(() => { onTranscriptRef.current = onTranscript }, [onTranscript])

  const stop = useCallback(() => {
    shouldKeepListeningRef.current = false
    setListening(false)
    const r = recognizerRef.current
    if (r) {
      try { r.stop() } catch { /* already stopped */ }
    }
  }, [])

  const start = useCallback(() => {
    if (!supported) return
    setError(null)
    setListening(true)
    shouldKeepListeningRef.current = true

    const r = new Ctor()
    r.lang = lang
    r.interimResults = false
    r.continuous = true
    r.maxAlternatives = 1

    r.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        if (result.isFinal) {
          const transcript = result[0]?.transcript ?? ''
          onTranscriptRef.current?.(transcript)
        }
      }
    }
    r.onerror = (event) => {
      setError(event.error || 'speech-error')
      // 'no-speech' is recoverable — the recognizer ends but we want to
      // keep the mic indicator lit so the user can keep talking after a
      // pause. Other errors (e.g. 'not-allowed') stop hard.
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setListening(false)
      }
    }
    r.onend = () => {
      // Re-arm so a quiet pause doesn't silently end the session — only stop
      // when the caller explicitly toggles off (which clears shouldKeepListeningRef).
      if (recognizerRef.current === r && shouldKeepListeningRef.current) {
        try { r.start() } catch { /* race with stop() */ }
      }
    }

    recognizerRef.current = r
    try { r.start() } catch (e) {
      setListening(false)
      setError(e?.message || 'failed-to-start')
    }
  }, [Ctor, lang, supported])

  // Clean up on unmount so a navigation away doesn't leak the mic.
  useEffect(() => {
    return () => {
      const r = recognizerRef.current
      if (r) {
        r.onresult = null
        r.onerror = null
        r.onend = null
        try { r.stop() } catch { /* already stopped */ }
        recognizerRef.current = null
      }
    }
  }, [])

  return { supported, listening, error, start, stop }
}
