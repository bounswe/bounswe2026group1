import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminDashboardPage from './AdminDashboardPage.jsx'

vi.mock('../../hooks/useAdmin.js', () => ({
  useAdminStats: vi.fn(),
}))

import { useAdminStats } from '../../hooks/useAdmin.js'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AdminDashboardPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading placeholders in stat cards', () => {
    useAdminStats.mockReturnValue({ data: undefined, isLoading: true, isError: false, error: null })
    renderPage()
    expect(screen.getAllByText('…').length).toBeGreaterThan(0)
  })

  it('renders stats when loaded', () => {
    useAdminStats.mockReturnValue({
      data: {
        totalUsers: 100,
        activeUsers: 90,
        bannedUsers: 2,
        totalReports: 50,
        pendingReports: 5,
        verifiedReports: 40,
        totalComments: 200,
      },
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage()
    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /admin overview/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /manage users/i })).toHaveAttribute('href', '/admin/users')
  })

  it('shows error banner', () => {
    useAdminStats.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('No permission'),
    })
    renderPage()
    expect(screen.getByText(/no permission/i)).toBeInTheDocument()
  })
})
