import { NavLink } from 'react-router-dom'

const links = [
  { to: '/admin', label: 'Overview', icon: 'dashboard', end: true },
  { to: '/admin/users', label: 'Users', icon: 'group' },
  { to: '/admin/reports', label: 'Reports', icon: 'flag' },
  { to: '/admin/comments', label: 'Comments', icon: 'forum' },
  { to: '/admin/validations', label: 'Validations', icon: 'how_to_vote' },
]

function AdminSidebar() {
  return (
    <aside className="w-full md:w-60 md:shrink-0 md:border-r border-outline-variant bg-surface-container-low md:bg-transparent">
      <nav className="flex md:flex-col gap-1 p-3 overflow-x-auto md:overflow-x-visible">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) =>
              [
                'flex items-center gap-3 px-3 py-2 rounded-lg whitespace-nowrap font-medium transition-colors',
                isActive
                  ? 'bg-primary-container text-on-primary-container'
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
              ].join(' ')
            }
          >
            <span className="material-symbols-outlined text-[20px]">{link.icon}</span>
            <span>{link.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

export default AdminSidebar
