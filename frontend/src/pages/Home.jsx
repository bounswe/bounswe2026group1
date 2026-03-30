import { useState } from 'react'
import ReportPanel from '../components/ReportPanel.jsx'

// Mock report data to simulate map pins
const MOCK_REPORTS = [
  {
    id: 1,
    title: 'Elevator out of service',
    description: 'The elevator at Boğaziçi metro stop has been out of service for 3 days. Wheelchair users cannot access the platform.',
    status: 'verified',
    date: 'March 28, 2026',
    location: 'Boğaziçi / Hisarüstü Metro Station',
    verifiedBy: 'Tyrion L.',
  },
  {
    id: 2,
    title: 'Broken pavement blocking sidewalk',
    description: 'Large sections of pavement are lifted and broken, making it impossible for wheelchair users to pass.',
    status: 'unverified',
    date: 'March 29, 2026',
    location: 'Beşiktaş, Ihlamur Caddesi',
    verifiedBy: null,
  },
  {
    id: 3,
    title: 'Construction debris on sidewalk',
    description: 'The sidewalk is completely blocked by construction waste, making wheelchair passage impossible.',
    status: 'unverified',
    date: 'March 30, 2026',
    location: 'Hisarüstü, Boğaziçi University entrance',
    verifiedBy: null,
  },
]

function Home() {
  const [selectedReport, setSelectedReport] = useState(null)

  return (
    <main className="relative min-h-screen bg-[#f0f1f1] font-body">

      {/* Map placeholder */}
      <div className="w-full h-screen bg-[#dde8d0] flex items-center justify-center relative overflow-hidden">

        {/* Fake map grid lines */}
        <div className="absolute inset-0 opacity-20"
          style={{
            backgroundImage: 'linear-gradient(#176a21 1px, transparent 1px), linear-gradient(90deg, #176a21 1px, transparent 1px)',
            backgroundSize: '60px 60px'
          }}
        />

        <p className="text-[#176a21] font-bold text-lg z-10 select-none">
          Map View — Click a pin to view report
        </p>

        {/* Mock report pins */}
        {MOCK_REPORTS.map((report, i) => (
          <button
            key={report.id}
            onClick={() => setSelectedReport(report)}
            className="absolute z-10 flex flex-col items-center group"
            style={{
              left: `${25 + i * 22}%`,
              top: `${30 + (i % 2) * 20}%`,
            }}
            aria-label={`Open report: ${report.title}`}
          >
            <span
              className={`material-symbols-outlined text-4xl drop-shadow-lg group-hover:scale-125 transition-transform ${
                report.status === 'verified' ? 'text-red-500' : 'text-amber-400'
              }`}
            >
              {report.status === 'verified' ? 'wrong_location' : 'help'}
            </span>
            <span className="text-xs font-bold text-[#2d2f2f] bg-white/80 px-2 py-0.5 rounded-full shadow mt-1 max-w-[100px] truncate">
              {report.title}
            </span>
          </button>
        ))}
      </div>

      {/* Report Panel */}
      <ReportPanel
        report={selectedReport}
        onClose={() => setSelectedReport(null)}
      />
    </main>
  )
}

export default Home