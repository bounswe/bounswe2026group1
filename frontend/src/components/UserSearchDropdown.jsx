import { useEffect, useRef } from 'react'
import UserSearchPanel from './UserSearchPanel.jsx'

/**
 * Navbar popover that mounts UserSearchPanel below the search icon — same
 * pattern as NotificationDropdown. Keeps the map visible behind so the user
 * doesn't lose context when looking someone up.
 *
 * Props:
 *  - onClose:    () => void   Fired on outside click, Escape, or after a
 *                             result is selected.
 *  - triggerRef: ref to the button that opens this dropdown — clicks inside
 *                it are ignored by the outside-click handler so the trigger
 *                can toggle the dropdown closed without racing the listener.
 */
function UserSearchDropdown({ onClose, triggerRef }) {
  const ref = useRef(null)

  useEffect(() => {
    function handlePointerDown(e) {
      if (ref.current?.contains(e.target)) return
      if (triggerRef?.current?.contains(e.target)) return
      onClose()
    }
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, triggerRef])

  return (
    <div
      ref={ref}
      className="absolute right-0 mt-2 w-96 max-h-[560px] flex flex-col gap-3 rounded-xl bg-surface-container-lowest shadow-lg ring-1 ring-outline-variant/30 z-[1100] overflow-y-auto p-4"
      role="dialog"
      aria-label="Find people"
    >
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold font-headline text-on-surface">Find people</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close search"
          className="w-8 h-8 rounded-full bg-error/10 text-error flex items-center justify-center hover:bg-error/20 transition-colors cursor-pointer"
        >
          <span className="material-symbols-outlined text-base">close</span>
        </button>
      </div>
      <UserSearchPanel onResultSelect={onClose} autoFocus={false} />
    </div>
  )
}

export default UserSearchDropdown
