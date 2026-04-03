import { useState, useEffect } from 'react'
import ReportPanel from '../components/ReportPanel.jsx'
import { getReports, mapReport } from '../services/reportService.js'

function Home() {
  const [reports, setReports] = useState([])
  const [selectedReport, setSelectedReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    async function fetchReports() {
      try {
        const data = await getReports()
        setReports(data.map(mapReport))
      } catch (err) {
        setError('Failed to load reports.')
        console.error(err)
      } finally {
        setLoading(false)
      }
    }
    fetchReports()
  }, [])

  return (
    <main className="flex h-screen overflow-hidden bg-background font-body">

      {/* Map */}
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
          {loading && (
            <p className="text-on-surface-variant font-bold text-lg select-none">Loading reports...</p>
          )}
          {error && (
            <p className="text-error font-bold text-lg select-none">{error}</p>
          )}
          {!loading && !error && reports.length === 0 && (
            <p className="text-on-surface-variant font-bold text-lg select-none">No reports found.</p>
          )}
          {!loading && !error && reports.length > 0 && (
            <p className="text-on-surface-variant font-bold text-lg select-none">
              Map View — Click a pin to view report
            </p>
          )}
        </div>

        {/* Report pins */}
        {reports.map((report, i) => (
          <button
            key={report.id}
            onClick={() => setSelectedReport(report)}
            className="absolute z-10 flex flex-col items-center group"
            style={{
              left: `${15 + (i % 5) * 16}%`,
              top: `${20 + Math.floor(i / 5) * 20}%`,
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

        {/* Zoom controls */}
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

      {/* Report Panel */}
      {selectedReport && (
        <ReportPanel
          report={selectedReport}
          onClose={() => setSelectedReport(null)}
          onVoteUpdate={(updatedReport) => {
            setReports(prev => prev.map(r => r.id === updatedReport.id ? updatedReport : r))
            setSelectedReport(updatedReport)
          }}
        />
      )}

    </main>
  )
}

export default Home