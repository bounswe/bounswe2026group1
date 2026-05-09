import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import SseStatusIndicator from './SseStatusIndicator.jsx'
import NotificationDropdown from './NotificationDropdown.jsx'
import ThemeToggleButton from './ThemePicker.jsx'
import logo from '../assets/mapcess-transparent.png'
import { useAuth } from '../context/AuthContext.jsx'
import { useCurrentUser } from '../hooks/useCurrentUser.js'
import { useUnreadCount } from '../hooks/useNotifications.js'

function Navbar() {
  const { isAuthenticated, isAdmin, logout } = useAuth()
  const { data: user } = useCurrentUser()
  const navigate = useNavigate()

  const unreadCount = useUnreadCount()
  const [menuOpen, setMenuOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [failedAvatarUrl, setFailedAvatarUrl] = useState(null)
  const menuRef = useRef(null)
  const buttonRef = useRef(null)
  const notifButtonRef = useRef(null)

  const avatarUrl = user?.avatarUrl
  const showAvatarImage = !!avatarUrl && failedAvatarUrl !== avatarUrl

  useEffect(() => {
    if (!menuOpen) return

    function handlePointerDown(event) {
      if (
        menuRef.current?.contains(event.target) ||
        buttonRef.current?.contains(event.target)
      ) {
        return
      }
      setMenuOpen(false)
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        buttonRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  function handleProfileClick() {
    setMenuOpen(false)
    navigate('/profile')
  }

  function handleLogoutClick() {
    setMenuOpen(false)
    logout()
    navigate('/')
  }

  return (
    <header className="w-full sticky top-0 z-[1001] bg-surface-container-lowest/80 backdrop-blur-md shadow-[0_4px_40px_-4px_rgba(45,47,47,0.08)] h-16 md:h-20 flex items-center justify-between px-4 sm:px-6 md:px-8 flex-shrink-0">
      <div className="flex items-center gap-4 md:gap-8">
        <div className="flex items-center gap-2 sm:gap-3 -translate-y-[2px]">
          <img
            src={logo}
            alt="Mapcess logo"
            className="h-12 md:h-18 w-auto block shrink-0"
          />
          <span className="text-xl md:text-2xl font-bold text-primary tracking-tight font-headline leading-none -translate-y-[1px]">
            Mapcess
          </span>
        </div>
        <nav className="hidden md:flex items-center gap-6">
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
            to="/feed"
            className={({ isActive }) =>
              isActive
                ? 'text-primary border-b-2 border-primary font-semibold pb-1'
                : 'text-on-surface-variant hover:text-primary transition-colors font-medium pb-1'
            }
          >
            Feed
          </NavLink>
          {isAdmin && (
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                isActive
                  ? 'text-primary border-b-2 border-primary font-semibold pb-1'
                  : 'text-on-surface-variant hover:text-primary transition-colors font-medium pb-1'
              }
            >
              Admin
            </NavLink>
          )}
        </nav>
      </div>

      <div className="flex items-center gap-1 sm:gap-2">
        <SseStatusIndicator />
        <ThemeToggleButton />
        {isAuthenticated && (
          <div className="relative hidden sm:block">
            <button
              ref={notifButtonRef}
              type="button"
              onClick={() => { setNotifOpen((prev) => !prev); setMenuOpen(false) }}
              className="flex shrink-0 w-10 h-10 rounded-full items-center justify-center hover:bg-surface-container transition-colors"
              aria-label="Notifications"
            >
              <span className="material-symbols-outlined text-on-surface-variant">notifications</span>
              {unreadCount > 0 && (
                <span className="absolute top-1 right-1 min-w-[18px] h-[18px] rounded-full bg-error text-on-error text-[10px] font-bold flex items-center justify-center px-1 leading-none pointer-events-none">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </button>
            {notifOpen && (
              <NotificationDropdown onClose={() => setNotifOpen(false)} />
            )}
          </div>
        )}
        <button
          className="hidden sm:flex shrink-0 w-10 h-10 rounded-full items-center justify-center hover:bg-surface-container transition-colors"
          aria-label="Settings"
        >
          <span className="material-symbols-outlined text-on-surface-variant">settings</span>
        </button>

        {isAuthenticated ? (
          <div className="relative ml-1 sm:ml-2">
            <button
              ref={buttonRef}
              type="button"
              onClick={() => setMenuOpen((prev) => !prev)}
              aria-label="Open profile menu"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              className="w-9 h-9 md:w-10 md:h-10 rounded-full bg-primary flex items-center justify-center overflow-hidden cursor-pointer ring-offset-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {showAvatarImage ? (
                <img
                  src={avatarUrl}
                  alt={user?.name ? `${user.name}'s avatar` : 'User avatar'}
                  className="w-full h-full object-cover"
                  onError={() => setFailedAvatarUrl(avatarUrl)}
                />
              ) : (
                <span className="material-symbols-outlined text-white" style={{ fontSize: '20px' }}>
                  person
                </span>
              )}
            </button>

            {menuOpen && (
              <div
                ref={menuRef}
                role="menu"
                aria-label="Profile menu"
                className="absolute right-0 mt-2 w-44 rounded-md bg-surface-container-lowest shadow-lg ring-1 ring-outline-variant/30 py-1 z-[1100]"
              >
                <button
                  type="button"
                  role="menuitem"
                  onClick={handleProfileClick}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-on-surface hover:bg-surface-container text-left cursor-pointer"
                >
                  <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '20px' }}>
                    person
                  </span>
                  Profile
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={handleLogoutClick}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-on-surface hover:bg-surface-container text-left cursor-pointer"
                >
                  <span className="material-symbols-outlined text-on-surface-variant" style={{ fontSize: '20px' }}>
                    logout
                  </span>
                  Log Out
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-2 ml-1 sm:ml-2">
            <Link
              to="/login"
              className="text-on-surface-variant hover:text-primary transition-colors font-medium text-sm sm:text-base"
            >
              Log In
            </Link>
            <Link
              to="/signup"
              className="bg-primary text-white rounded-full px-3 py-1.5 sm:px-4 sm:py-2 hover:bg-primary/90 transition-colors font-semibold text-sm sm:text-base"
            >
              Sign Up
            </Link>
          </div>
        )}
      </div>
    </header>
  )
}

export default Navbar
