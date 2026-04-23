import { NavLink } from 'react-router-dom'
import SseStatusIndicator from './SseStatusIndicator.jsx'

function Navbar() {
  return (
    <header className="w-full sticky top-0 z-[1001] bg-white/80 backdrop-blur-md shadow-[0_4px_40px_-4px_rgba(45,47,47,0.08)] h-16 md:h-20 flex items-center justify-between px-4 sm:px-6 md:px-8 flex-shrink-0">
      <div className="flex items-baseline gap-4 md:gap-8">
        <span className="text-xl md:text-2xl font-bold text-primary tracking-tight font-headline">
          Mapcess
        </span>
        <nav className="hidden md:flex items-baseline gap-6">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold pb-1'
                : 'text-on-surface-variant hover:text-primary transition-colors font-medium pb-1'
            }
          >
            Home
          </NavLink>
          <NavLink
            to="/reports"
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold pb-1'
                : 'text-on-surface-variant hover:text-primary transition-colors font-medium pb-1'
            }
          >
            Reports
          </NavLink>
        </nav>
      </div>

      <div className="flex items-center gap-1 sm:gap-2">
        <SseStatusIndicator />
        <button
          className="hidden sm:flex shrink-0 w-10 h-10 rounded-full items-center justify-center hover:bg-surface-container transition-colors"
          aria-label="Notifications"
        >
          <span className="material-symbols-outlined text-on-surface-variant">notifications</span>
        </button>
        <button
          className="hidden sm:flex shrink-0 w-10 h-10 rounded-full items-center justify-center hover:bg-surface-container transition-colors"
          aria-label="Settings"
        >
          <span className="material-symbols-outlined text-on-surface-variant">settings</span>
        </button>
        <div className="w-9 h-9 md:w-10 md:h-10 rounded-full bg-primary ml-1 sm:ml-2 flex items-center justify-center">
          <span className="material-symbols-outlined text-white" style={{ fontSize: '20px' }}>person</span>
        </div>
      </div>
    </header>
  )
}

export default Navbar
