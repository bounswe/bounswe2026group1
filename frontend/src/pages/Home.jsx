import { useState } from 'react'
import ReportPanel from '../components/ReportPanel.jsx'

const MOCK_REPORTS = [
  {
    id: 1,
    title: 'Elevator out of service',
    description: 'The elevator at Boğaziçi metro stop has been out of service for 3 days. Wheelchair users cannot access the platform.',
    status: 'verified',
    date: 'Reported 2h ago',
    location: 'Boğaziçi / Hisarüstü Metro Station',
    reportedBy: 'Tyrion L.',
    agrees: 84,
    disagrees: 7,
    tags: ['Elevator', 'Metro'],
    image: null,
  },
  {
    id: 2,
    title: 'Broken pavement blocking sidewalk',
    description: 'Large sections of pavement are lifted and broken, making it impossible for wheelchair users to pass.',
    status: 'unverified',
    date: 'Reported 5h ago',
    location: 'Beşiktaş, Ihlamur Caddesi',
    reportedBy: 'Ceren K.',
    agrees: 12,
    disagrees: 2,
    tags: ['Pavement', 'Safety Hazard'],
    image: null,
  },
  {
    id: 3,
    title: 'Construction debris on sidewalk',
    description: 'The sidewalk is completely blocked by construction waste, making wheelchair passage impossible.',
    status: 'unverified',
    date: 'Reported 1d ago',
    location: 'Hisarüstü, Boğaziçi University entrance',
    reportedBy: 'Eftelya S.',
    agrees: 5,
    disagrees: 1,
    tags: ['Construction', 'Blocked Path'],
    image: null,
  },
]

function Home() {
  const [selectedReport, setSelectedReport] = useState(null)

  return (
    <main className="flex h-screen overflow-hidden bg-background font-body">

      {/* Map — takes remaining space, shrinks when panel opens */}
      <section className="relative flex-1 bg-surface-container">

        {/* Map grid placeholder */}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage: 'linear-gradient(#176a21 1px, transparent 1px), linear-gradient(90deg, #176a21 1px, transparent 1px)',
            backgroundSize: '60px 60px',
          }}
        />

        {/* Center label */}
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <p className="text-on-surface-variant font-bold text-lg select-none">
            Map View — Click a pin to view report
          </p>
        </div>

        {/* Mock pins */}
        {MOCK_REPORTS.map((report, i) => (
          <button
            key={report.id}
            onClick={() => setSelectedReport(report)}
            className="absolute z-10 flex flex-col items-center group"
            style={{
              left: `${20 + i * 22}%`,
              top: `${30 + (i % 2) * 20}%`,
            }}
            aria-label={`Open report: ${report.title}`}
          >
            <div className={`p-3 rounded-full shadow-lg ring-4 ring-white/30 active:scale-90 transition-transform ${
              report.status === 'verified' ? 'bg-primary text-on-primary' : 'bg-amber-400 text-white'
            }`}>
              <span className="material-symbols-outlined">warning</span>
            </div>
            <div className="mt-2 px-3 py-1 bg-white rounded-full text-xs font-bold shadow-sm border border-outline-variant/20 max-w-[120px] truncate">
              {report.title}
            </div>
          </button>
        ))}

        {/* Map zoom controls */}
        <div className="absolute bottom-8 left-8 flex flex-col gap-2">
          <button className="w-12 h-12 bg-surface-container-lowest rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-bright transition-colors text-on-surface">
            <span className="material-symbols-outlined">add</span>
          </button>
          <button className="w-12 h-12 bg-surface-container-lowest rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-bright transition-colors text-on-surface">
            <span className="material-symbols-outlined">remove</span>
          </button>
          <button className="w-12 h-12 bg-surface-container-lowest rounded-xl flex items-center justify-center shadow-lg hover:bg-surface-bright transition-colors text-on-surface mt-2">
            <span className="material-symbols-outlined">my_location</span>
          </button>
        </div>
      </section>

      {/* Report Panel — right sidebar */}
      {selectedReport && (
        <ReportPanel
          report={selectedReport}
          onClose={() => setSelectedReport(null)}
        />
      )}

    </main>
  )
}

export default Home