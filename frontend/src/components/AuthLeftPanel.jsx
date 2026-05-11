// AuthLeftPanel.jsx
function AuthLeftPanel({ headline, description }) {
  const MINT = '#d1ffc8'
  const INK  = '#06381a'

  return (
    <section className="hidden md:flex md:w-1/2 lg:w-3/5 relative overflow-hidden bg-[#06381a]">
      {/* Route-map illustration. viewBox is authored at the AuthLeftPanel
          aspect (~1200×1100) and the route + waypoints sit in a center
          safe-zone (x:220..940, y:280..880) so neither md:w-1/2 nor
          lg:w-3/5 cropping removes any of them. */}
      <svg
        viewBox="0 0 1200 1100"
        preserveAspectRatio="xMidYMid slice"
        className="absolute inset-0 w-full h-full"
        aria-hidden="true"
      >
        <defs>
          <pattern id="hero-grid" width="60" height="60" patternUnits="userSpaceOnUse">
            <path d="M60 0H0V60" fill="none" stroke="rgba(209,255,200,0.09)" strokeWidth="1" />
          </pattern>
          <pattern id="hero-grid-fine" width="15" height="15" patternUnits="userSpaceOnUse">
            <path d="M15 0H0V15" fill="none" stroke="rgba(209,255,200,0.045)" strokeWidth="0.5" />
          </pattern>
          <radialGradient id="hero-vignette" cx="50%" cy="42%" r="80%">
            <stop offset="0%"   stopColor="rgba(2,93,22,0)" />
            <stop offset="100%" stopColor="rgba(6,40,18,0.7)" />
          </radialGradient>
        </defs>

        <rect width="1200" height="1100" fill="url(#hero-grid-fine)" />
        <rect width="1200" height="1100" fill="url(#hero-grid)" />

        {/* streets — extended past viewBox so edges tile cleanly under any crop */}
        <g stroke="rgba(209,255,200,0.18)" strokeWidth="24" fill="none" strokeLinecap="square">
          <line x1="-100" y1="280"  x2="1300" y2="280" />
          <line x1="-100" y1="600"  x2="1300" y2="600" />
          <line x1="-100" y1="880"  x2="1300" y2="880" />
          <line x1="220"  y1="-100" x2="220"  y2="1200" />
          <line x1="600"  y1="-100" x2="600"  y2="1200" />
          <line x1="940"  y1="-100" x2="940"  y2="1200" />
        </g>

        {/* accessible route */}
        <polyline
          points="220,280 600,280 600,600 940,600 940,880"
          fill="none" stroke={MINT} strokeWidth="7"
          strokeLinecap="round" strokeLinejoin="round"
          strokeDasharray="2 16"
        />
        <polyline
          points="220,280 600,280 600,600 940,600 940,880"
          fill="none" stroke={MINT} strokeWidth="3"
          strokeLinecap="round" strokeLinejoin="round" opacity="0.55"
        />

        {/* waypoints */}
        <circle cx="220" cy="280" r="11" fill={MINT} />
        <circle cx="220" cy="280" r="24" fill="none" stroke={MINT} strokeWidth="1.5" opacity="0.5" />
        <rect   x="588" y="268" width="24" height="24" transform="rotate(45 600 280)" fill={MINT} />
        <circle cx="600" cy="600" r="10" fill={MINT} />
        <rect   x="928" y="588" width="24" height="24" transform="rotate(45 940 600)" fill={MINT} />
        <circle cx="940" cy="880" r="16" fill={INK} stroke={MINT} strokeWidth="3.5" />

        <rect width="1200" height="1100" fill="url(#hero-vignette)" />
      </svg>

      {/* readability scrim */}
      <div className="absolute inset-0 z-10 bg-gradient-to-t from-[#06381a]/60 via-transparent to-transparent" />

      {/* text overlay — unchanged from your version */}
      <div className="relative z-20 flex flex-col justify-between h-full p-16 text-[#d1ffc8]">
        <span className="text-3xl font-headline font-bold italic tracking-tight">
          Mapcess
        </span>
        <div className="max-w-xl">
          <h2 className="font-headline text-5xl font-extrabold leading-tight mb-6">
            {headline}
          </h2>
          <p className="text-lg opacity-90 leading-relaxed font-light">
            {description}
          </p>
        </div>
      </div>
    </section>
  )
}

export default AuthLeftPanel