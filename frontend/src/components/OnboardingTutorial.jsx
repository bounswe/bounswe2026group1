import { useState, useEffect, useRef, useCallback } from 'react'

import rampDimensions  from '../assets/tutorial/ramp-dimensions.png'
import rampDetail      from '../assets/tutorial/ramp-detail.png'
import curbRamp        from '../assets/tutorial/curb-ramp.png'
import streetView      from '../assets/tutorial/street-view.png'
import elevatorCabin   from '../assets/tutorial/elevator-cabin.png'
import elevatorEntrance from '../assets/tutorial/elevator-entrance.png'
import doorGlass       from '../assets/tutorial/door-glass.png'
import doorHandles     from '../assets/tutorial/door-handles.png'

/**
 * First-visit accessibility-standards onboarding for the web app.
 *
 * Image-hero design: each topic slide pairs a diagram from Özlem Belir's
 * "Mimari Erişilebilirlik Kılavuzu" with a short bullet list. Every bullet
 * ends with a "report as <issue>" tag matching an enum value in
 * backend/src/main/java/com/bounswe2026group1/backend/model/IssueType.java
 * so the tutorial doubles as a reporting reference.
 *
 * Persistence is the parent's responsibility — this component just calls
 * onClose() when the user dismisses, completes, or hits Escape.
 */

const AUDIENCE_ICON = {
  mob:  'accessible',
  vis:  'visibility',
  hear: 'hearing',
  eld:  'elderly',
}

const AUDIENCE_STYLE = {
  mob:  { background: '#DAEFDB', color: '#1B5E20' },
  vis:  { background: '#D4E6F8', color: '#0D47A1' },
  hear: { background: '#FBE0DA', color: '#8D2200' },
  eld:  { background: '#E8DEF8', color: '#3E1F8A' },
}

function AudienceChip({ group }) {
  const style = AUDIENCE_STYLE[group]
  return (
    <span
      className="inline-flex items-center justify-center w-[26px] h-[26px] rounded-full flex-shrink-0"
      style={style}
      aria-hidden="true"
    >
      <span
        className="material-symbols-outlined text-base"
        style={{ fontSize: 16, fontVariationSettings: "'FILL' 1, 'wght' 500" }}
      >
        {AUDIENCE_ICON[group]}
      </span>
    </span>
  )
}

/* ─────────────────────────  Diagrams  ───────────────────────── */

function WelcomeDiagram() {
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto max-h-full"
      role="img" aria-label="Stylised top-down map with accessibility report markers">
      <defs>
        <linearGradient id="ot-map-bg" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#c8d8c9" />
          <stop offset="40%" stopColor="#b5cdb7" />
          <stop offset="100%" stopColor="#a8c4ab" />
        </linearGradient>
        <pattern id="ot-grid" patternUnits="userSpaceOnUse" width="40" height="40">
          <path d="M40 0 L0 0 0 40" fill="none" stroke="rgba(255,255,255,0.22)" strokeWidth="1" />
        </pattern>
        <pattern id="ot-park" patternUnits="userSpaceOnUse" width="14" height="14">
          <rect width="14" height="14" fill="#9fbb9d" />
          <circle cx="3" cy="4" r="1.4" fill="#7ea482" opacity="0.7" />
          <circle cx="9" cy="9" r="1.2" fill="#7ea482" opacity="0.6" />
        </pattern>
      </defs>
      <rect width="720" height="360" fill="url(#ot-map-bg)" />
      <rect width="720" height="360" fill="url(#ot-grid)" />
      <rect x="585" y="20" width="115" height="42" fill="url(#ot-park)" rx="4" />
      <g fill="#e8e6dd" stroke="#b9b6ab" strokeWidth="1">
        <rect x="20"  y="14"  width="130" height="40" rx="3" />
        <rect x="190" y="14"  width="160" height="40" rx="3" />
        <rect x="395" y="14"  width="155" height="40" rx="3" />
        <rect x="20"  y="92"  width="130" height="62" rx="3" />
        <rect x="190" y="92"  width="160" height="62" rx="3" />
        <rect x="395" y="92"  width="155" height="62" rx="3" />
        <rect x="585" y="92"  width="115" height="62" rx="3" />
        <rect x="20"  y="194" width="130" height="68" rx="3" />
        <rect x="190" y="194" width="160" height="68" rx="3" />
        <rect x="395" y="194" width="155" height="68" rx="3" />
        <rect x="585" y="194" width="115" height="68" rx="3" />
        <rect x="20"  y="304" width="130" height="44" rx="3" />
        <rect x="190" y="304" width="160" height="44" rx="3" />
        <rect x="395" y="304" width="155" height="44" rx="3" />
        <rect x="585" y="304" width="115" height="44" rx="3" />
      </g>
      <g fill="#fafafa" stroke="#cfd0cf" strokeWidth="1">
        <rect x="0"   y="60"  width="720" height="22" />
        <rect x="0"   y="160" width="720" height="22" />
        <rect x="0"   y="270" width="720" height="22" />
        <rect x="160" y="0"   width="22"  height="360" />
        <rect x="360" y="0"   width="22"  height="360" />
        <rect x="555" y="0"   width="22"  height="360" />
      </g>
      {[
        { cx: 140, cy: 110, color: '#2E7D32', icon: 'accessible_forward' },
        { cx: 340, cy: 90,  color: '#8B6A00', icon: 'door_front' },
        { cx: 560, cy: 120, color: '#495F69', icon: 'directions_walk' },
        { cx: 210, cy: 270, color: '#176a21', icon: 'elevator' },
        { cx: 430, cy: 245, color: '#2E7D32', icon: 'accessible_forward' },
        { cx: 620, cy: 260, color: '#176a21', icon: 'directions_walk' },
      ].map((m, i) => (
        <g key={i} filter="drop-shadow(0 4px 12px rgba(0,0,0,0.15))">
          <circle cx={m.cx} cy={m.cy} r="20" fill="#fff" stroke={m.color} strokeWidth="2.5" />
          <text
            x={m.cx} y={m.cy}
            fontFamily="Material Symbols Outlined"
            fontSize="20"
            fill={m.color}
            textAnchor="middle"
            dominantBaseline="central"
            style={{ fontVariationSettings: "'FILL' 1" }}
          >
            {m.icon}
          </text>
        </g>
      ))}
    </svg>
  )
}

function HowToReportDiagram() {
  const steps = [
    { icon: 'touch_app',   label: 'Tap the map' },
    { icon: 'add_a_photo', label: 'Add media' },
    { icon: 'edit_note',   label: 'Describe' },
    { icon: 'send',        label: 'Submit' },
  ]
  return (
    <div className="flex items-center justify-center flex-wrap gap-1.5 py-2">
      {steps.map((s, i) => (
        <div key={s.label} className="flex items-center">
          <div className="flex flex-col items-center gap-1.5 px-3 py-3 rounded-xl bg-surface-container-low min-w-[96px]">
            <span className="material-symbols-outlined text-primary" style={{ fontSize: 30 }}>{s.icon}</span>
            <span className="text-[12.5px] font-semibold text-on-surface whitespace-nowrap">{s.label}</span>
          </div>
          {i < steps.length - 1 && (
            <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: 22 }}>chevron_right</span>
          )}
        </div>
      ))}
    </div>
  )
}

function PhotoFrame({ src, alt }) {
  // Fills its parent (which sets the height in the side-by-side layout)
  // and centers the image so the original aspect ratio is preserved.
  return (
    <div className="bg-white rounded-lg p-3 flex items-center justify-center w-full h-full min-h-0">
      <img
        src={src}
        alt={alt}
        loading="lazy"
        className="block object-contain"
        style={{ maxWidth: '100%', maxHeight: '100%' }}
      />
    </div>
  )
}

/* ─────────────────────────  Slide content  ─────────────────────────
 * Every topic bullet ends with "report as <name>" where <name> matches
 * an IssueType enum value in IssueType.java (rendered in lowercase for
 * users). If you add an issue type, add it here too.
 */

const SLIDES = [
  {
    id: 'welcome',
    title: 'Welcome to Mapcess',
    icon: 'explore',
    badgeColor: 'var(--color-primary)',
    diagram: <WelcomeDiagram />,
    tipTitle: "What you'll learn",
    tip: "A quick tour of the most common things people report — ramps, curb cuts, sidewalks, elevators, and doors — and how to file your first report in under a minute. The diagrams are adapted from Özlem Belir's Mimari Erişilebilirlik Kılavuzu.",
  },
  {
    id: 'ramps',
    title: 'Ramps',
    icon: 'accessible_forward',
    badgeColor: '#2E7D32',
    bullets: [
      { jsx: <>Slope steeper than <strong>1:12</strong> — report as <strong>too steep</strong></>, aud: ['mob'] },
      { jsx: <>Run longer than <strong>9 m</strong> without a <strong>≥ 1.5 m</strong> landing — report as <strong>no landing</strong></>, aud: ['mob'] },
      { jsx: <>No handrails — or only one side — report as <strong>missing handrail</strong></>, aud: ['mob', 'eld', 'vis'] },
      { jsx: <>Handrails present but mounted below <strong>90 cm</strong> — report as <strong>handrail too low</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>Clear width between handrails under <strong>87 cm</strong> — report as <strong>too narrow</strong></>, aud: ['mob'] },
    ],
    diagram: <PhotoFrame src={rampDimensions} alt="Isometric ramp diagram showing handrail heights 800–920 mm, maximum slope 1:12, 9 m maximum run with 1.5 m landings" />,
    diagramCaption: <>Max slope <em className="not-italic font-bold text-primary-dim">1:12</em> · max run <em className="not-italic font-bold text-primary-dim">9 m</em> · landings <em className="not-italic font-bold text-primary-dim">≥ 1.5 m</em></>,
  },
  {
    id: 'ramps-detail',
    title: 'Ramps — landings & stairs',
    icon: 'stairs',
    badgeColor: '#2E7D32',
    bullets: [
      { jsx: <>Landing smaller than <strong>120 cm</strong> at the top or bottom — report as <strong>no landing</strong></>, aud: ['mob'] },
      { jsx: <>Ramp width under <strong>150 cm</strong> — report as <strong>too narrow</strong></>, aud: ['mob'] },
      { jsx: <>Stair steps without anti-slip strips — report as <strong>no anti-slip</strong></>, aud: ['mob', 'eld', 'vis'] },
      { jsx: <>Stair edges without a contrasting <strong>nosing strip</strong> — report as <strong>missing nosing strip</strong></>, aud: ['vis', 'eld'] },
      { jsx: <>Step risers above <strong>16 cm</strong> or treads under <strong>27 cm</strong> — report as <strong>riser too high</strong> / <strong>tread too shallow</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>Steps of inconsistent size or with open vertical faces — report as <strong>irregular steps</strong> / <strong>open risers</strong></>, aud: ['mob', 'eld', 'vis'] },
    ],
    diagram: <PhotoFrame src={rampDetail} alt="Detailed cutaway of a ramp meeting a stair: handrails at 900 mm, landings at 1200 mm, anti-slip treads, tactile paving, and a side profile or curb" />,
    diagramCaption: 'Where the ramp meets stairs and a doorway',
  },
  {
    id: 'curb-cuts',
    title: 'Curb cuts',
    icon: 'roundabout_right',
    badgeColor: '#176A21',
    bullets: [
      { jsx: <>Slope steeper than <strong>1:10</strong> — report as <strong>too steep</strong></>, aud: ['mob'] },
      { jsx: <>Bottom width under <strong>91 cm</strong> — report as <strong>too narrow</strong></>, aud: ['mob'] },
      { jsx: <>No <strong>tactile warning surface</strong> where the curb meets the road — report as <strong>no tactile warning</strong></>, aud: ['vis'] },
      { jsx: <>Raised lip at the gutter that catches wheels — report as <strong>raised lip</strong></>, aud: ['mob'] },
      { jsx: <>No curb cut at all where one is needed — report as <strong>missing</strong></>, aud: ['mob', 'vis'] },
    ],
    diagram: <PhotoFrame src={curbRamp} alt="Wheelchair user crossing a curb ramp with a 1:10 slope and minimum 915 mm bottom width" />,
    diagramCaption: <>Where the sidewalk meets the crossing · <em className="not-italic font-bold text-primary-dim">1:10</em> slope · <em className="not-italic font-bold text-primary-dim">≥ 915 mm</em> wide</>,
  },
  {
    id: 'sidewalks',
    title: 'Sidewalks',
    icon: 'directions_walk',
    badgeColor: '#495F69',
    bullets: [
      { jsx: <>Clear path narrower than <strong>1.2 m</strong> — report as <strong>too narrow</strong></>, aud: ['mob'] },
      { jsx: <>Awnings, signs or branches under <strong>2.2 m</strong> overhead — report as <strong>insufficient clearance</strong></>, aud: ['mob', 'vis', 'eld'] },
      { jsx: <>Parked vehicles, furniture, or debris in the path — report as <strong>blocked</strong></>, aud: ['mob', 'vis'] },
      { jsx: <>Cracks, potholes, or damage that could trip someone — report as <strong>uneven surface</strong></>, aud: ['mob', 'eld', 'vis'] },
      { jsx: <>Slippery when wet or worn smooth — report as <strong>slippery surface</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>No tactile paving along the route — report as <strong>no tactile paving</strong></>, aud: ['vis'] },
      { jsx: <>Level change over <strong>1.3 cm</strong> that isn't ramped — report as <strong>unramped level difference</strong></>, aud: ['mob'] },
    ],
    diagram: <PhotoFrame src={streetView} alt="Three-dimensional street view showing maximum sign and awning heights and clear pedestrian path widths" />,
    diagramCaption: 'Street view — signs and awnings stay above head height',
  },
  {
    id: 'elevators-cabin',
    title: 'Elevators — cabin',
    icon: 'elevator',
    badgeColor: '#B02500',
    bullets: [
      { jsx: <>Cabin narrower than <strong>120 cm</strong> or under <strong>1.80 m²</strong> floor area — report as <strong>cabin too small</strong></>, aud: ['mob'] },
      { jsx: <>Door opening under <strong>90 cm</strong> — report as <strong>door too narrow</strong></>, aud: ['mob'] },
      { jsx: <>Approach area in front of the doors under <strong>120–150 cm</strong> — report as <strong>insufficient landing</strong></>, aud: ['mob'] },
      { jsx: <>No grab bar inside the cabin — report as <strong>no grab bar</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>Elevator exists but isn't working — report as <strong>out of service</strong></>, aud: ['mob', 'vis', 'eld'] },
    ],
    diagram: <PhotoFrame src={elevatorCabin} alt="Top-down elevator cabin diagram: 1730 mm wide by 1370 mm deep with a 915 mm clear door opening" />,
    diagramCaption: <>Cabin <em className="not-italic font-bold text-primary-dim">≥ 173 × 137 cm</em> · door <em className="not-italic font-bold text-primary-dim">≥ 915 mm</em></>,
  },
  {
    id: 'elevators-panel',
    title: 'Elevators — entrance & panel',
    icon: 'elevator',
    badgeColor: '#B02500',
    bullets: [
      { jsx: <>Buttons mounted outside <strong>85–120 cm</strong> — report as <strong>button height invalid</strong></>, aud: ['mob'] },
      { jsx: <>No Braille or raised tactile labels on the buttons — report as <strong>no braille</strong></>, aud: ['vis'] },
      { jsx: <>No spoken floor announcement — report as <strong>no audio</strong></>, aud: ['vis'] },
      { jsx: <>Cabin or controls poorly lit — report as <strong>insufficient lighting</strong></>, aud: ['vis', 'eld'] },
    ],
    diagram: <PhotoFrame src={elevatorEntrance} alt="Elevator entrance: 1830 mm door height, 1500 mm panel height, 735 mm button height, up/down arrow indicator" />,
    diagramCaption: <>Buttons at <em className="not-italic font-bold text-primary-dim">~735 mm</em> · panel top <em className="not-italic font-bold text-primary-dim">≤ 1500 mm</em> · door <em className="not-italic font-bold text-primary-dim">≥ 1830 mm</em> high</>,
  },
  {
    id: 'doors-glass',
    title: 'Doors — glass & contrast',
    icon: 'door_front',
    badgeColor: '#8B6A00',
    bullets: [
      { jsx: <>Clear width under <strong>90 cm</strong> when fully open — report as <strong>too narrow</strong></>, aud: ['mob'] },
      { jsx: <>Large glass surfaces without contrast bands — report as <strong>no glass marking</strong></>, aud: ['vis'] },
      { jsx: <>Threshold sill higher than <strong>0.6 cm</strong> — report as <strong>high threshold</strong></>, aud: ['mob'] },
      { jsx: <>Steps at the entrance where a ramp is needed — report as <strong>step at entrance</strong></>, aud: ['mob'] },
      { jsx: <>Building entrance without an automatic / sensor door — report as <strong>no automatic door</strong></>, aud: ['mob', 'eld'] },
    ],
    diagram: <PhotoFrame src={doorGlass} alt="Front view of a glass entry door showing two horizontal contrast bands between 500 mm and 1.5 m and a lever handle" />,
    diagramCaption: <>Contrast bands between <em className="not-italic font-bold text-primary-dim">50 cm</em> and <em className="not-italic font-bold text-primary-dim">1.5 m</em></>,
  },
  {
    id: 'doors-handles',
    title: 'Doors — handles',
    icon: 'door_open',
    badgeColor: '#8B6A00',
    bullets: [
      { jsx: <>Round knob instead of a lever or pull-bar — report as <strong>no lever handle</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>Handle mounted outside <strong>90–110 cm</strong> — report as <strong>handle height invalid</strong></>, aud: ['mob'] },
      { jsx: <>Door needs excessive force to open — report as <strong>heavy door</strong></>, aud: ['mob', 'eld'] },
      { jsx: <>Intercom or doorbell outside <strong>90–140 cm</strong> or not approachable from the side — report as <strong>intercom inaccessible</strong></>, aud: ['mob'] },
    ],
    diagram: <PhotoFrame src={doorHandles} alt="Comparison of door hardware: a round knob crossed out as inaccessible, contrasted with two long lever handles" />,
    diagramCaption: <>Knob (left): <em className="not-italic font-bold text-primary-dim">avoid</em> · Lever / pull bar (right): <em className="not-italic font-bold text-primary-dim">accessible</em></>,
  },
  {
    id: 'how-to',
    title: 'How to file a report',
    icon: 'add_location_alt',
    badgeColor: 'var(--color-primary)',
    diagram: <HowToReportDiagram />,
    tipTitle: 'Done in under a minute',
    tip: 'Once five neighbours agree, your report becomes verified and starts shaping accessible routes for everyone.',
  },
]

/* ─────────────────────────  Component  ───────────────────────── */

export default function OnboardingTutorial({ onClose }) {
  const [index, setIndex] = useState(0)
  const closeButtonRef = useRef(null)
  const total = SLIDES.length
  const isLast = index === total - 1
  const slide = SLIDES[index]

  const goNext = useCallback(() => {
    setIndex((i) => (i === total - 1 ? i : i + 1))
  }, [total])

  const goBack = useCallback(() => {
    setIndex((i) => (i === 0 ? i : i - 1))
  }, [])

  useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape') onClose()
      else if (event.key === 'ArrowRight') goNext()
      else if (event.key === 'ArrowLeft') goBack()
    }
    document.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose, goNext, goBack])

  function handleBackdropClick(event) {
    if (event.target === event.currentTarget) onClose()
  }

  function handlePrimary() {
    if (isLast) onClose()
    else goNext()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="onboarding-title"
      onClick={handleBackdropClick}
      className="fixed inset-0 z-[1500] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-xl w-full max-w-[960px] h-[780px] max-h-[calc(100vh-48px)] flex flex-col p-5 gap-3">
        {/* Top bar — dot indicator + close button */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2" aria-hidden="true">
            {SLIDES.map((_, i) => (
              <span
                key={i}
                className={`h-2 rounded-full transition-all ${
                  i === index ? 'w-6 bg-primary' : 'w-2 bg-surface-container-high'
                }`}
              />
            ))}
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close tutorial"
            className="w-9 h-9 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-xl">close</span>
          </button>
        </div>

        {/* Compact title strip */}
        <div className="flex items-center gap-3 flex-shrink-0">
          <div
            className="rounded-full grid place-items-center w-11 h-11 flex-shrink-0"
            style={{
              backgroundColor: `color-mix(in srgb, ${slide.badgeColor} 14%, transparent)`,
              color: slide.badgeColor,
            }}
            aria-hidden="true"
          >
            <span
              className="material-symbols-outlined"
              style={{ fontSize: 26, fontVariationSettings: "'wght' 500" }}
            >
              {slide.icon}
            </span>
          </div>
          <h2 id="onboarding-title" className="text-xl font-bold font-headline text-on-surface m-0">
            {slide.title}
          </h2>
        </div>

        {/* Body — image left, supporting text right (no vertical scrolling) */}
        <div className="flex-1 min-h-0 flex flex-row gap-4 px-0.5">
          {slide.diagram && (
            <div className="flex-1 min-w-0 bg-surface-container-low border border-surface-container-high rounded-xl px-3 pt-3 pb-2 flex flex-col">
              <div className="flex-1 min-h-0 flex items-center justify-center">
                {slide.diagram}
              </div>
              {slide.diagramCaption && (
                <div className="text-center text-[13px] font-medium text-on-surface-variant mt-2 flex-shrink-0">
                  {slide.diagramCaption}
                </div>
              )}
            </div>
          )}

          <div className="w-[320px] flex-shrink-0 flex flex-col gap-3 overflow-y-auto">
            {slide.bullets && (
              <ul className="list-none p-0 m-0 text-left text-[13.5px] leading-snug text-on-surface flex flex-col gap-1.5">
                {slide.bullets.map((bullet, i) => (
                  <li key={i} className="flex items-start gap-2.5 py-1">
                    <span
                      className="material-symbols-outlined text-primary flex-shrink-0 mt-0.5"
                      style={{ fontSize: 18, fontVariationSettings: "'FILL' 1" }}
                      aria-hidden="true"
                    >
                      check_circle
                    </span>
                    <span className="flex-1 onboarding-bullet">{bullet.jsx}</span>
                    <span className="inline-flex gap-1 flex-shrink-0">
                      {bullet.aud.map((group) => (
                        <AudienceChip key={group} group={group} />
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
            )}

            {slide.tip && (
              <div className="bg-surface-container-low border-l-[3px] border-primary text-[13.5px] leading-relaxed py-2.5 px-3.5 rounded text-on-surface text-left">
                <div className="text-[11px] font-bold uppercase tracking-wider text-primary-dim mb-0.5">
                  {slide.tipTitle ?? 'Tip'}
                </div>
                {slide.tip}
              </div>
            )}
          </div>
        </div>

        {/* Citation */}
        <div className="flex-shrink-0 text-[11.5px] italic text-center text-on-surface-variant pt-0.5">
          Diagrams adapted from{' '}
          <span className="not-italic font-semibold text-on-surface">Özlem Belir</span>
          , <span>Mimari Erişilebilirlik Kılavuzu</span>
        </div>

        {/* Bottom bar — skip + back/next */}
        <div className="flex items-center justify-between pt-3 border-t border-surface-container">
          <button
            type="button"
            onClick={onClose}
            className={`px-3 py-2 rounded-md text-sm font-semibold text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low cursor-pointer ${isLast ? 'invisible' : ''}`}
          >
            Skip tour
          </button>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={goBack}
              disabled={index === 0}
              className="px-5 py-2 rounded-full text-sm font-semibold bg-surface-container hover:bg-surface-container-high text-on-surface disabled:opacity-0 disabled:pointer-events-none cursor-pointer"
            >
              Back
            </button>
            <button
              type="button"
              onClick={handlePrimary}
              className="px-5 py-2 rounded-full text-sm font-semibold bg-primary hover:bg-primary-dim text-on-primary inline-flex items-center gap-1 cursor-pointer"
            >
              <span>{isLast ? 'Got it' : 'Next'}</span>
              <span className="material-symbols-outlined" style={{ fontSize: 18 }}>
                {isLast ? 'check' : 'arrow_forward'}
              </span>
            </button>
          </div>
        </div>
      </div>

      <style>{`
        .onboarding-bullet strong { color: var(--color-primary-dim); font-weight: 700; }
      `}</style>
    </div>
  )
}
