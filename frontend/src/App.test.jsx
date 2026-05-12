import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './context/AuthContext.jsx'
import { ThemeProvider } from './context/ThemeContext.jsx'
import { SseProvider } from './context/SseContext.jsx'
import App from './App.jsx'

vi.mock('./hooks/useSseSync.js', () => ({ useSseSync: vi.fn() }))
vi.mock('./hooks/useNotificationSseSync.js', () => ({ useNotificationSseSync: vi.fn() }))

vi.mock('./pages/Home.jsx', () => ({ default: () => <div data-testid="page-home">Home</div> }))
vi.mock('./pages/Signup.jsx', () => ({ default: () => <div data-testid="page-signup">Signup</div> }))
vi.mock('./pages/Login.jsx', () => ({ default: () => <div data-testid="page-login">Login</div> }))
vi.mock('./pages/Feed.jsx', () => ({ default: () => <div data-testid="page-feed">Feed</div> }))
vi.mock('./pages/Leaderboard.jsx', () => ({ default: () => <div data-testid="page-leaderboard">Leaderboard</div> }))
vi.mock('./pages/ProfilePage.jsx', () => ({ default: () => <div data-testid="page-profile">Profile</div> }))
vi.mock('./pages/UserProfilePage.jsx', () => ({ default: () => <div data-testid="page-user-profile">UserProfile</div> }))
vi.mock('./pages/UserSearchPage.jsx', () => ({ default: () => <div data-testid="page-user-search">UserSearch</div> }))
vi.mock('./pages/admin/AdminDashboardPage.jsx', () => ({
  default: () => <div data-testid="page-admin-dash">AdminDash</div>,
}))
vi.mock('./pages/admin/AdminUsersPage.jsx', () => ({
  default: () => <div data-testid="page-admin-users">AdminUsers</div>,
}))
vi.mock('./pages/admin/AdminReportsPage.jsx', () => ({
  default: () => <div data-testid="page-admin-reports">AdminReports</div>,
}))
vi.mock('./pages/admin/AdminCommentsPage.jsx', () => ({
  default: () => <div data-testid="page-admin-comments">AdminComments</div>,
}))
vi.mock('./pages/admin/AdminValidationsPage.jsx', () => ({
  default: () => <div data-testid="page-admin-validations">AdminValidations</div>,
}))
vi.mock('./components/admin/AdminLayout.jsx', async () => {
  const { Outlet } = await import('react-router-dom')
  return {
    default: function AdminLayoutMock() {
      return (
        <div data-testid="admin-layout">
          <Outlet />
        </div>
      )
    },
  }
})
vi.mock('./components/RequireAuth.jsx', async () => {
  const { Outlet } = await import('react-router-dom')
  return { default: function RequireAuthMock() { return <Outlet /> } }
})
vi.mock('./components/RequireAdmin.jsx', async () => {
  const { Outlet } = await import('react-router-dom')
  return { default: function RequireAdminMock() { return <Outlet /> } }
})

function renderApp(initialEntry = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <ThemeProvider>
          <SseProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </SseProvider>
        </ThemeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('App routing integration', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('shows home at /', () => {
    renderApp('/')
    expect(screen.getByTestId('page-home')).toBeInTheDocument()
  })

  it('shows login at /login', () => {
    renderApp('/login')
    expect(screen.getByTestId('page-login')).toBeInTheDocument()
  })

  it('shows signup at /signup', () => {
    renderApp('/signup')
    expect(screen.getByTestId('page-signup')).toBeInTheDocument()
  })

  it('renders profile when RequireAuth passes through', () => {
    renderApp('/profile')
    expect(screen.getByTestId('page-profile')).toBeInTheDocument()
  })

  it('renders nested admin route', () => {
    renderApp('/admin/users')
    expect(screen.getByTestId('admin-layout')).toBeInTheDocument()
    expect(screen.getByTestId('page-admin-users')).toBeInTheDocument()
  })
})
