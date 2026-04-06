import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { useTheme } from '../context/ThemeContext.jsx'

function Navbar() {
  const { isAuthenticated, userId, token } = useAuth()
  const { isDark, toggleTheme } = useTheme()
  const navigate = useNavigate()

  function getUserName() {
    if (!token) return null
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
      const payload = JSON.parse(atob(base64))
      return payload.name || payload.sub || null
    } catch {
      return null
    }
  }

  const userName = getUserName()
  const initials = userName
    ? userName.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
    : null

  return (
    <header className="w-full sticky top-0 z-[1001] bg-surface-container-low/80 backdrop-blur-md shadow-[0_4px_40px_-4px_rgba(45,47,47,0.08)] h-20 flex items-center justify-between px-8 flex-shrink-0">
      <div className="flex items-baseline gap-8">
        <span className="text-2xl font-bold text-primary tracking-tight font-headline">
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

      <div className="flex items-center gap-2">

        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className="shrink-0 rounded-full flex items-center justify-center hover:bg-surface-container transition-colors"
          style={{ width: '40px', height: '40px' }}
          aria-label="Toggle theme"
        >
          <span className="material-symbols-outlined text-on-surface-variant">
            {isDark ? 'light_mode' : 'dark_mode'}
          </span>
        </button>

        {/* Notifications */}
        <button
          className="shrink-0 rounded-full flex items-center justify-center hover:bg-surface-container transition-colors"
          style={{ width: '40px', height: '40px' }}
          aria-label="Notifications"
        >
          <span className="material-symbols-outlined text-on-surface-variant">notifications</span>
        </button>

        {/* Settings */}
        <button
          className="shrink-0 rounded-full flex items-center justify-center hover:bg-surface-container transition-colors"
          style={{ width: '40px', height: '40px' }}
          aria-label="Settings"
        >
          <span className="material-symbols-outlined text-on-surface-variant">settings</span>
        </button>

        {/* User avatar / login */}
        {isAuthenticated ? (
          <div className="flex items-center gap-2 ml-2">
            {userName && (
              <span className="text-sm font-semibold text-on-surface hidden md:block">
                {userName.split(' ')[0]}
              </span>
            )}
            <div className="w-10 h-10 rounded-full bg-primary flex items-center justify-center">
              {initials ? (
                <span className="text-on-primary text-sm font-bold">{initials}</span>
              ) : (
                <span className="material-symbols-outlined text-on-primary" style={{ fontSize: '20px' }}>person</span>
              )}
            </div>
          </div>
        ) : (
          <button
            onClick={() => navigate('/login')}
            className="ml-2 px-4 py-2 bg-primary text-on-primary rounded-full text-sm font-bold hover:bg-primary-dim transition-colors"
          >
            Log in
          </button>
        )}
      </div>
    </header>
  )
}

export default Navbar