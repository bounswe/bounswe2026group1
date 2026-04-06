import { useEffect, useState } from 'react'

/**
 * Toast
 * Props:
 *  - message: string
 *  - type: 'success' | 'error' (default: 'success')
 *  - duration: ms before auto-dismiss (default: 3000)
 *  - onDismiss: () => void
 */
function Toast({ message, type = 'success', duration = 3000, onDismiss }) {
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false)
      setTimeout(onDismiss, 300)
    }, duration)
    return () => clearTimeout(timer)
  }, [duration, onDismiss])

  const colors = type === 'success'
    ? 'bg-primary text-on-primary'
    : 'bg-error text-on-error'

  const icon = type === 'success' ? 'check_circle' : 'error'

  return (
    <div className={`fixed bottom-8 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-3 px-6 py-4 rounded-2xl shadow-lg transition-all duration-300 ${colors} ${visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
      <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>
        {icon}
      </span>
      <p className="text-sm font-bold">{message}</p>
    </div>
  )
}

export default Toast