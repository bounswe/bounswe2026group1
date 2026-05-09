import { useState, useEffect, useRef, useCallback } from 'react'

/**
 * First-visit accessibility-standards onboarding for the web app.
 * Six slides: Welcome → Ramps → Sidewalks → Elevators → Doors → How to file a report.
 * Each category slide carries 3-4 bullets tagged by audience group (mob / vis / hear / eld)
 * and a stylised SVG diagram. Persistence is the parent's responsibility — this component
 * just calls onClose() when the user dismisses, completes, or hits Escape.
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

/* ─────────────────────────  SVG diagrams  ───────────────────────── */

function WelcomeDiagram() {
  // Stylised top-down map: gradient base + grid + buildings + streets + a park
  // and 6 report markers using the same circle+border+icon style as Home.jsx.
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto" role="img"
      aria-label="Stylised top-down map with accessibility report markers">
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
        <rect x="20" y="14" width="130" height="40" rx="3" />
        <rect x="190" y="14" width="160" height="40" rx="3" />
        <rect x="395" y="14" width="155" height="40" rx="3" />
        <rect x="20" y="92" width="130" height="62" rx="3" />
        <rect x="190" y="92" width="160" height="62" rx="3" />
        <rect x="395" y="92" width="155" height="62" rx="3" />
        <rect x="585" y="92" width="115" height="62" rx="3" />
        <rect x="20" y="194" width="130" height="68" rx="3" />
        <rect x="190" y="194" width="160" height="68" rx="3" />
        <rect x="395" y="194" width="155" height="68" rx="3" />
        <rect x="585" y="194" width="115" height="68" rx="3" />
        <rect x="20" y="304" width="130" height="44" rx="3" />
        <rect x="190" y="304" width="160" height="44" rx="3" />
        <rect x="395" y="304" width="155" height="44" rx="3" />
        <rect x="585" y="304" width="115" height="44" rx="3" />
      </g>
      <g fill="#fafafa" stroke="#cfd0cf" strokeWidth="1">
        <rect x="0" y="60" width="720" height="22" />
        <rect x="0" y="160" width="720" height="22" />
        <rect x="0" y="270" width="720" height="22" />
        <rect x="160" y="0" width="22" height="360" />
        <rect x="360" y="0" width="22" height="360" />
        <rect x="555" y="0" width="22" height="360" />
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

function RampDiagram() {
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto" role="img"
      aria-label="Ramp side profile with handrail and tactile warning strips">
      <defs>
        <pattern id="ot-hatch-r" patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)">
          <line x1="0" y1="0" x2="0" y2="6" stroke="#9ca3af" strokeWidth="1" />
        </pattern>
        <pattern id="ot-tactile" patternUnits="userSpaceOnUse" width="7" height="7">
          <rect width="7" height="7" fill="#f4a900" />
          <circle cx="3.5" cy="3.5" r="1.4" fill="#8b6a00" />
        </pattern>
        <linearGradient id="ot-map-bg-r" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#c8d8c9" />
          <stop offset="40%" stopColor="#b5cdb7" />
          <stop offset="100%" stopColor="#a8c4ab" />
        </linearGradient>
        <pattern id="ot-grid-r" patternUnits="userSpaceOnUse" width="40" height="40">
          <path d="M40 0 L0 0 0 40" fill="none" stroke="rgba(255,255,255,0.22)" strokeWidth="1" />
        </pattern>
      </defs>
      <rect width="720" height="360" fill="url(#ot-map-bg-r)" />
      <rect width="720" height="360" fill="url(#ot-grid-r)" />
      <rect x="0" y="290" width="720" height="60" fill="url(#ot-hatch-r)" />
      <line x1="0" y1="290" x2="720" y2="290" stroke="#2d2f2f" strokeWidth="1.8" strokeLinecap="round" />
      <polygon points="130,290 570,210 650,210 650,290" fill="#c4e3c5" stroke="#176a21" strokeWidth="2" />
      <rect x="78" y="284" width="48" height="11" fill="url(#ot-tactile)" stroke="#8b6a00" strokeWidth="1" />
      <rect x="568" y="200" width="74" height="11" fill="url(#ot-tactile)" stroke="#8b6a00" strokeWidth="1" />
      <line x1="130" y1="290" x2="122.8" y2="250.6" stroke="#2d2f2f" strokeWidth="1.4" strokeDasharray="4,3" />
      <line x1="570" y1="210" x2="562.8" y2="170.6" stroke="#2d2f2f" strokeWidth="1.4" strokeDasharray="4,3" />
      <polyline points="92.8,250.6 122.8,250.6 562.8,170.6 592.8,170.6"
        fill="none" stroke="#2d2f2f" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" />
      <text x="528" y="218"
        fontFamily="Material Symbols Outlined" fontSize="56" fill="#176a21"
        textAnchor="middle" style={{ fontVariationSettings: "'wght' 500" }}>
        accessible_forward
      </text>
      <text x="107" y="244" fontFamily="Inter, sans-serif" fontSize="14" fontWeight="600" fill="#5a5c5c" textAnchor="middle">30</text>
      <text x="577" y="164" fontFamily="Inter, sans-serif" fontSize="14" fontWeight="600" fill="#5a5c5c" textAnchor="middle">30</text>
      <line x1="60" y1="290" x2="60" y2="250.6" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="54" y1="290" x2="66" y2="290" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="54" y1="250.6" x2="66" y2="250.6" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="66" y1="250.6" x2="92.8" y2="250.6" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <text x="60" y="275" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="end" dx="-6">90 cm</text>
      <line x1="130" y1="318" x2="570" y2="318" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="130" y1="312" x2="130" y2="324" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="570" y1="312" x2="570" y2="324" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="350" y="345" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="middle">L (length)</text>
      <line x1="680" y1="210" x2="680" y2="290" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="674" y1="210" x2="686" y2="210" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="674" y1="290" x2="686" y2="290" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="650" y1="210" x2="680" y2="210" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <text x="694" y="255" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="start">H</text>
    </svg>
  )
}

function SidewalkDiagram() {
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto" role="img"
      aria-label="Top-down sidewalk plan: width across, two wheelchairs passing, overhead branch, contrast strip">
      <rect x="0" y="0" width="720" height="55" fill="#dbdddd" stroke="#2d2f2f" strokeWidth="1.5" />
      <text x="22" y="34" fontFamily="Inter, sans-serif" fontSize="12" fontWeight="700" letterSpacing="0.08em" fill="#5a5c5c">BUILDING</text>
      <rect x="0" y="55" width="720" height="170" fill="#e6e3d8" stroke="#2d2f2f" strokeWidth="1.5" />
      <rect x="0" y="225" width="720" height="10" fill="#f4a900" stroke="#8b6a00" strokeWidth="1" />
      <rect x="0" y="235" width="720" height="125" fill="#5a5c5c" opacity="0.45" />
      <line x1="0" y1="297" x2="720" y2="297" stroke="#fff" strokeWidth="2" strokeDasharray="22,16" opacity="0.7" />
      <text x="22" y="335" fontFamily="Inter, sans-serif" fontSize="12" fontWeight="700" letterSpacing="0.08em" fill="#f0eee5">ROAD</text>
      <ellipse cx="380" cy="115" rx="72" ry="42"
        fill="rgba(120,170,110,0.4)" stroke="rgba(80,130,75,0.7)" strokeWidth="1.5" strokeDasharray="4,3" />
      <text x="380" y="115" fontFamily="Material Symbols Outlined" fontSize="36" fill="#558358"
        textAnchor="middle" dominantBaseline="central" style={{ fontVariationSettings: "'FILL' 1" }}>park</text>
      <g filter="drop-shadow(0 3px 6px rgba(0,0,0,0.20))">
        <circle cx="200" cy="100" r="22" fill="#fff" stroke="#495f69" strokeWidth="2.8" />
        <text x="200" y="100" fontFamily="Material Symbols Outlined" fontSize="22" fill="#495f69"
          textAnchor="middle" dominantBaseline="central" style={{ fontVariationSettings: "'FILL' 1" }}>accessible</text>
      </g>
      <text x="234" y="100" fontFamily="Material Symbols Outlined" fontSize="22" fill="#495f69"
        textAnchor="start" dominantBaseline="central" style={{ fontVariationSettings: "'FILL' 1" }}>arrow_forward</text>
      <g filter="drop-shadow(0 3px 6px rgba(0,0,0,0.20))">
        <circle cx="540" cy="180" r="22" fill="#fff" stroke="#495f69" strokeWidth="2.8" />
        <text x="540" y="180" fontFamily="Material Symbols Outlined" fontSize="22" fill="#495f69"
          textAnchor="middle" dominantBaseline="central" style={{ fontVariationSettings: "'FILL' 1" }}>accessible</text>
      </g>
      <text x="506" y="180" fontFamily="Material Symbols Outlined" fontSize="22" fill="#495f69"
        textAnchor="end" dominantBaseline="central" style={{ fontVariationSettings: "'FILL' 1" }}>arrow_back</text>
      <line x1="48" y1="55" x2="48" y2="225" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="42" y1="55" x2="54" y2="55" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="42" y1="225" x2="54" y2="225" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="48" y="138" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f"
        textAnchor="middle" transform="rotate(-90 48 138)">≥ 150 cm width</text>
      <line x1="448" y1="90" x2="585" y2="32" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <rect x="555" y="14" width="160" height="32" fill="#fff" stroke="#5a5c5c" strokeWidth="1.5" rx="6" />
      <text x="635" y="34" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="middle">↕ ≥ 220 cm overhead</text>
      <line x1="100" y1="230" x2="100" y2="270" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <text x="100" y="285" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="middle">contrast strip</text>
    </svg>
  )
}

function ElevatorDiagram() {
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto" role="img"
      aria-label="Elevator top-down floor plan with cabin, door, braille panel, floor display and alarm">
      <rect x="220" y="80" width="280" height="200" fill="#fbe5dc" opacity="0.55" />
      <line x1="220" y1="80" x2="500" y2="80" stroke="#2d2f2f" strokeWidth="2.4" strokeLinecap="round" />
      <line x1="220" y1="80" x2="220" y2="280" stroke="#2d2f2f" strokeWidth="2.4" strokeLinecap="round" />
      <line x1="500" y1="80" x2="500" y2="280" stroke="#2d2f2f" strokeWidth="2.4" strokeLinecap="round" />
      <line x1="220" y1="280" x2="270" y2="280" stroke="#2d2f2f" strokeWidth="2.4" strokeLinecap="round" />
      <line x1="450" y1="280" x2="500" y2="280" stroke="#2d2f2f" strokeWidth="2.4" strokeLinecap="round" />
      <text x="360" y="210" fontFamily="Material Symbols Outlined" fontSize="80" fill="#b02500"
        textAnchor="middle" style={{ fontVariationSettings: "'wght' 500" }}>accessible</text>
      <rect x="470" y="115" width="22" height="46" fill="#fff" stroke="#2d2f2f" strokeWidth="1.4" rx="2" />
      <text x="481" y="133" fontFamily="Inter, sans-serif" fontSize="10" fontWeight="700" textAnchor="middle" fill="#2d2f2f">13</text>
      <circle cx="476" cy="143" r="1.2" fill="#2d2f2f" />
      <circle cx="481" cy="143" r="1.2" fill="#2d2f2f" />
      <circle cx="476" cy="148" r="1.2" fill="#2d2f2f" />
      <circle cx="481" cy="153" r="1.2" fill="#2d2f2f" />
      <line x1="492" y1="138" x2="540" y2="118" stroke="#5a5c5c" strokeWidth="1.2" strokeDasharray="2,3" />
      <text x="546" y="122" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="start">Braille buttons</text>
      <rect x="330" y="92" width="60" height="28" fill="#1a2a32" stroke="#2d2f2f" strokeWidth="1.4" rx="3" />
      <text x="346" y="113" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="800" textAnchor="middle" fill="#7be08a">↑</text>
      <text x="372" y="114" fontFamily="Inter, sans-serif" fontSize="18" fontWeight="800" textAnchor="middle" fill="#7be08a">13</text>
      <line x1="360" y1="92" x2="360" y2="62" stroke="#5a5c5c" strokeWidth="1.2" strokeDasharray="2,3" />
      <text x="360" y="56" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="middle">Floor display</text>
      <circle cx="240" cy="135" r="9" fill="#fff" stroke="#b02500" strokeWidth="2" />
      <text x="240" y="140" fontFamily="Material Symbols Outlined" fontSize="14" fill="#b02500" textAnchor="middle">notifications_active</text>
      <circle cx="240" cy="135" r="14" fill="none" stroke="#b02500" strokeWidth="1" opacity="0.5" strokeDasharray="3,3" />
      <line x1="231" y1="135" x2="195" y2="135" stroke="#5a5c5c" strokeWidth="1.2" strokeDasharray="2,3" />
      <text x="190" y="139" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="end">Flash alarm</text>
      <line x1="220" y1="50" x2="500" y2="50" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="220" y1="44" x2="220" y2="56" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="500" y1="44" x2="500" y2="56" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="360" y="40" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="middle">≥ 140 cm</text>
      <line x1="190" y1="80" x2="190" y2="280" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="184" y1="80" x2="196" y2="80" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="184" y1="280" x2="196" y2="280" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="180" y="184" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f"
        textAnchor="middle" transform="rotate(-90 180 184)">≥ 120 cm</text>
      <line x1="270" y1="310" x2="450" y2="310" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="270" y1="304" x2="270" y2="316" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="450" y1="304" x2="450" y2="316" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="360" y="332" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="middle">≥ 90 cm door</text>
      <text x="540" y="285" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#5a5c5c" textAnchor="start">↑ door</text>
    </svg>
  )
}

function DoorDiagram() {
  return (
    <svg viewBox="0 0 720 360" xmlns="http://www.w3.org/2000/svg" className="block w-full h-auto" role="img"
      aria-label="Door front view with glass panel, contrast bands, lever handle and clear-width dimension">
      <defs>
        <pattern id="ot-hatch-d" patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)">
          <line x1="0" y1="0" x2="0" y2="6" stroke="#9ca3af" strokeWidth="1" />
        </pattern>
      </defs>
      <rect x="100" y="40" width="520" height="260" fill="#f0f1f1" />
      <rect x="280" y="50" width="180" height="250" fill="#fff5e0" stroke="#8b6a00" strokeWidth="3" />
      <rect x="290" y="58" width="160" height="242" fill="#d4a661" stroke="#8b6a00" strokeWidth="1.5" />
      <rect x="305" y="72" width="130" height="128" fill="#dde8ef" stroke="#5a8aa8" strokeWidth="1.2" rx="2" />
      <line x1="320" y1="82" x2="425" y2="195" stroke="#fff" strokeWidth="1" opacity="0.6" />
      <rect x="305" y="105" width="130" height="6" fill="#f4a900" />
      <rect x="305" y="155" width="130" height="6" fill="#f4a900" />
      <rect x="305" y="215" width="130" height="68" fill="none" stroke="#8b6a00" strokeWidth="1" rx="2" />
      <rect x="295" y="208" width="22" height="6" fill="#5a5c5c" rx="2" />
      <circle cx="307" cy="211" r="3.5" fill="#2d2f2f" />
      <line x1="100" y1="300" x2="620" y2="300" stroke="#2d2f2f" strokeWidth="1.8" strokeLinecap="round" />
      <rect x="280" y="295" width="180" height="5" fill="#5a5c5c" />
      <rect x="100" y="300" width="520" height="40" fill="url(#ot-hatch-d)" />
      <line x1="290" y1="30" x2="450" y2="30" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="290" y1="24" x2="290" y2="36" stroke="#5a5c5c" strokeWidth="1.2" />
      <line x1="450" y1="24" x2="450" y2="36" stroke="#5a5c5c" strokeWidth="1.2" />
      <text x="370" y="20" fontFamily="Inter, sans-serif" fontSize="16" fontWeight="700" fill="#2d2f2f" textAnchor="middle">≥ 90 cm clear</text>
      <line x1="317" y1="211" x2="540" y2="200" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <rect x="540" y="184" width="140" height="32" fill="#fff" stroke="#5a5c5c" strokeWidth="1.5" rx="6" />
      <text x="610" y="204" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="middle">Lever handle</text>
      <line x1="305" y1="108" x2="220" y2="100" stroke="#5a5c5c" strokeWidth="1" strokeDasharray="2,3" />
      <rect x="80" y="83" width="140" height="32" fill="#fff" stroke="#5a5c5c" strokeWidth="1.5" rx="6" />
      <text x="150" y="103" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="600" fill="#2d2f2f" textAnchor="middle">Contrast band</text>
    </svg>
  )
}

function HowToReportDiagram() {
  // Four-step row: tap → describe → add media → submit
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
          <div className="flex flex-col items-center gap-1.5 px-2.5 py-2 rounded-xl bg-surface-container-low min-w-[76px]">
            <span className="material-symbols-outlined text-primary" style={{ fontSize: 26 }}>{s.icon}</span>
            <span className="text-[11.5px] font-semibold text-on-surface whitespace-nowrap">{s.label}</span>
          </div>
          {i < steps.length - 1 && (
            <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: 20 }}>chevron_right</span>
          )}
        </div>
      ))}
    </div>
  )
}

/* ─────────────────────────  Slide content  ───────────────────────── */

const SLIDES = [
  {
    id: 'welcome',
    title: 'Welcome to Mapcess',
    icon: 'explore',
    badgeColor: 'var(--color-primary)',
    diagram: <WelcomeDiagram />,
    tipTitle: "What you'll learn",
    tip: 'A quick tour of the four most common things people report — ramps, sidewalks, elevators, and doors — and how to file your first report in under a minute.',
  },
  {
    id: 'ramps',
    title: 'Ramps',
    icon: 'accessible_forward',
    badgeColor: '#2E7D32',
    bullets: [
      { jsx: <>Slope <strong>≤ 10%</strong> — steeper blocks wheelchairs</>, aud: ['mob'] },
      { jsx: <>Clear width <strong>≥ 100 cm</strong> — room for a walker + helper</>, aud: ['mob'] },
      { jsx: <>Handrails on both sides at <strong>90 cm</strong> (extra rail at 70 cm for shorter users)</>, aud: ['mob', 'eld', 'vis'] },
      { jsx: <><strong>Tactile warning strip</strong> at the top and bottom of the ramp</>, aud: ['vis'] },
    ],
    diagram: <RampDiagram />,
    diagramCaption: <>slope = H ÷ L · accessible when <em className="not-italic font-bold text-primary-dim">≤ 10%</em></>,
  },
  {
    id: 'sidewalks',
    title: 'Sidewalks',
    icon: 'directions_walk',
    badgeColor: '#495F69',
    bullets: [
      { jsx: <>Clear width <strong>≥ 150 cm</strong> — wheelchairs need to pass each other</>, aud: ['mob'] },
      { jsx: <>Overhead clearance <strong>≥ 220 cm</strong> — no low branches or signs</>, aud: ['mob', 'vis', 'eld'] },
      { jsx: <><strong>Contrasting strip</strong> along the curb edge so it's visible against the road</>, aud: ['vis'] },
    ],
    diagram: <SidewalkDiagram />,
    diagramCaption: 'Top-down view of the sidewalk',
  },
  {
    id: 'elevators',
    title: 'Elevators',
    icon: 'elevator',
    badgeColor: '#B02500',
    bullets: [
      { jsx: <>Door clear width <strong>≥ 90 cm</strong> when fully open</>, aud: ['mob'] },
      { jsx: <><strong>Braille labels</strong> on every button and voice announcement</>, aud: ['vis'] },
      { jsx: <><strong>Visual floor display</strong> + flashing emergency alarm</>, aud: ['hear'] },
    ],
    diagram: <ElevatorDiagram />,
    diagramCaption: 'Top-down view of the cabin',
  },
  {
    id: 'doors',
    title: 'Doors',
    icon: 'door_front',
    badgeColor: '#8B6A00',
    bullets: [
      { jsx: <>Clear width <strong>≥ 90 cm</strong> when fully open</>, aud: ['mob'] },
      { jsx: <><strong>Lever handle</strong> (not a knob) — easier with weak grip</>, aud: ['mob', 'eld'] },
      { jsx: <>Glass panels marked with <strong>contrasting bands</strong> at eye level</>, aud: ['vis'] },
    ],
    diagram: <DoorDiagram />,
    diagramCaption: 'Front view — entrance door',
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
      <div className="bg-surface-container-lowest rounded-2xl shadow-xl w-full max-w-[600px] h-[680px] max-h-[calc(100vh-48px)] flex flex-col p-6 gap-3">
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
            className="w-9 h-9 rounded-full hover:bg-surface-container-low flex items-center justify-center cursor-pointer text-on-surface-variant"
          >
            <span className="material-symbols-outlined text-xl">close</span>
          </button>
        </div>

        {/* Slide content (scrollable when tall) */}
        <div className="flex-1 min-h-0 overflow-y-auto flex flex-col items-center text-center px-1">
          <div className="flex flex-col items-center" style={{ flexShrink: 0 }}>
            <div
              className="rounded-full grid place-items-center w-[72px] h-[72px] mb-3"
              style={{
                backgroundColor: `color-mix(in srgb, ${slide.badgeColor} 14%, transparent)`,
                color: slide.badgeColor,
              }}
              aria-hidden="true"
            >
              <span
                className="material-symbols-outlined"
                style={{ fontSize: 40, fontVariationSettings: "'wght' 500" }}
              >
                {slide.icon}
              </span>
            </div>
            <h2 id="onboarding-title" className="text-2xl font-bold font-headline text-on-surface mb-2">
              {slide.title}
            </h2>
          </div>

          {slide.bullets && (
            <ul className="self-stretch list-none p-0 m-0 mb-3 max-w-[440px] mx-auto text-left text-[13.5px] leading-tight text-on-surface flex flex-col gap-1.5">
              {slide.bullets.map((bullet, i) => (
                <li key={i} className="flex items-center gap-2.5 py-1.5">
                  <span
                    className="material-symbols-outlined text-primary flex-shrink-0"
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

          {slide.diagram && (
            <div className="w-full max-w-[440px] bg-surface-container-low border border-surface-container-high rounded-xl px-3 pt-2.5 pb-1.5 mb-3">
              {slide.diagram}
              {slide.diagramCaption && (
                <div className="text-center text-[12.5px] font-medium text-on-surface-variant mt-0.5">
                  {slide.diagramCaption}
                </div>
              )}
            </div>
          )}

          {slide.tip && (
            <div className="self-stretch bg-surface-container-low border-l-[3px] border-primary text-[13.5px] leading-relaxed py-2.5 px-3.5 rounded text-on-surface text-left max-w-[440px] mx-auto">
              <div className="text-[11px] font-bold uppercase tracking-wider text-primary-dim mb-0.5">
                {slide.tipTitle ?? 'Tip'}
              </div>
              {slide.tip}
            </div>
          )}
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

      {/* Style measurement <strong> in bullets to match the mock — primary-dim and bold */}
      <style>{`
        .onboarding-bullet strong { color: var(--color-primary-dim); font-weight: 700; }
      `}</style>
    </div>
  )
}
