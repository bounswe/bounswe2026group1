import { Link } from 'react-router-dom'
import StatCard from '../../components/admin/StatCard.jsx'
import { useAdminStats } from '../../hooks/useAdmin.js'

const QUICK_LINKS = [
  { to: '/admin/users', label: 'Manage Users', icon: 'group' },
  { to: '/admin/reports', label: 'Manage Reports', icon: 'flag' },
  { to: '/admin/comments', label: 'Manage Comments', icon: 'forum' },
  { to: '/admin/validations', label: 'Manage Validations', icon: 'how_to_vote' },
]

function AdminDashboardPage() {
  const { data, isLoading, isError, error } = useAdminStats()

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl md:text-3xl font-bold font-headline text-on-surface">Admin Overview</h1>
        <p className="text-on-surface-variant mt-1">
          High-level snapshot of platform activity and moderation surface area.
        </p>
      </header>

      {isError && (
        <div className="rounded-xl bg-error-container text-on-error-container px-4 py-3">
          {error?.message || 'Failed to load stats'}
        </div>
      )}

      <section className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <StatCard label="Total Users" value={isLoading ? '…' : data?.totalUsers} icon="group" />
        <StatCard
          label="Active Users"
          value={isLoading ? '…' : data?.activeUsers}
          icon="check_circle"
          accent="tertiary"
        />
        <StatCard
          label="Banned Users"
          value={isLoading ? '…' : data?.bannedUsers}
          icon="block"
          accent="error"
        />
        <StatCard
          label="Total Reports"
          value={isLoading ? '…' : data?.totalReports}
          icon="flag"
          accent="secondary"
        />
        <StatCard
          label="Pending Reports"
          value={isLoading ? '…' : data?.pendingReports}
          icon="hourglass_empty"
          accent="tertiary"
        />
        <StatCard
          label="Verified Reports"
          value={isLoading ? '…' : data?.verifiedReports}
          icon="verified"
        />
        <StatCard
          label="Total Comments"
          value={isLoading ? '…' : data?.totalComments}
          icon="forum"
          accent="secondary"
        />
      </section>

      <section>
        <h2 className="text-lg font-semibold font-headline text-on-surface mb-3">Quick Links</h2>
        <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
          {QUICK_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className="flex items-center gap-3 p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant hover:bg-surface-container transition-colors"
            >
              <span className="material-symbols-outlined text-primary">{link.icon}</span>
              <span className="font-semibold text-on-surface">{link.label}</span>
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}

export default AdminDashboardPage
