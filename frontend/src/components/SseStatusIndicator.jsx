import { useSseStatus } from '../context/SseContext.jsx'

const CONFIG = {
  connected: { color: 'bg-green-500', label: 'Live' },
  reconnecting: { color: 'bg-amber-400', label: 'Reconnecting…' },
  disconnected: { color: 'bg-red-500', label: 'Offline' },
}

/**
 * Small connection status pill shown in the Navbar.
 * Hidden when connected to keep the UI clean during normal operation.
 */
export default function SseStatusIndicator() {
  const status = useSseStatus()

  if (status === 'connected') return null

  const { color, label } = CONFIG[status] ?? CONFIG.disconnected

  return (
    <span className="flex items-center gap-1.5 text-xs font-medium text-gray-600 px-2 py-1 rounded-full bg-gray-100">
      <span className={`w-2 h-2 rounded-full ${color} animate-pulse`} />
      {label}
    </span>
  )
}
