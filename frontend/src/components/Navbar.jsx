import { NavLink } from 'react-router-dom'

function Navbar() {
  return (
    <header className="w-full sticky top-0 z-[1001] bg-white/80 backdrop-blur-md shadow-[0_4px_40px_-4px_rgba(45,47,47,0.08)] h-20 flex items-center justify-between px-8 flex-shrink-0">
      <div className="flex items-center gap-8">
        <span className="text-2xl font-bold text-primary tracking-tight font-headline">
          Mapcess
        </span>
        <nav className="hidden md:flex items-center gap-6">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold py-1'
                : 'text-secondary hover:text-primary transition-colors font-medium py-1'
            }
          >
            Home
          </NavLink>
          <NavLink
            to="/reports"
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold py-1'
                : 'text-secondary hover:text-primary transition-colors font-medium py-1'
            }
          >
            Reports
          </NavLink>
          <NavLink
            to="/profile"
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold py-1'
                : 'text-secondary hover:text-primary transition-colors font-medium py-1'
            }
          >
            Profile
          </NavLink>
        </nav>
      </div>

      <div className="flex items-center gap-2">
        <button
          className="p-2 rounded-full hover:bg-surface-container-low transition-colors active:scale-95"
          aria-label="Notifications"
        >
          <span className="material-symbols-outlined text-secondary">notifications</span>
        </button>
        <button
          className="p-2 rounded-full hover:bg-surface-container-low transition-colors active:scale-95"
          aria-label="Settings"
        >
          <span className="material-symbols-outlined text-secondary">settings</span>
        </button>
        <div className="w-10 h-10 rounded-full overflow-hidden bg-surface-container ml-2 flex items-center justify-center">
          <span className="material-symbols-outlined text-secondary">person</span>
        </div>
      </div>
    </header>
  )
}

export default Navbar
