import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminUsersPage from './AdminUsersPage.jsx'

vi.mock('../../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../../services/adminService.js', () => ({
  getAdminUsers: vi.fn(),
  getAdminUser: vi.fn(),
  createAdminUser: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn(),
  changeUserRole: vi.fn(),
  deleteAdminUser: vi.fn(),
  getAdminStats: vi.fn(),
  getAdminReports: vi.fn(),
  changeReportStatus: vi.fn(),
  deleteAdminReport: vi.fn(),
  getAdminComments: vi.fn(),
  deleteAdminComment: vi.fn(),
  getAdminValidations: vi.fn(),
  deleteAdminValidation: vi.fn(),
}))

import { useAuth } from '../../context/AuthContext.jsx'
import * as adminService from '../../services/adminService.js'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminUsersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const SAMPLE_PAGE = {
  content: [
    {
      id: 1,
      name: 'Ada Lovelace',
      email: 'ada@example.com',
      role: 'USER',
      status: 'ACTIVE',
      registeredAt: '2026-01-01T00:00:00Z',
      points: 42,
    },
    {
      id: 2,
      name: 'Bob Banned',
      email: 'bob@example.com',
      role: 'USER',
      status: 'BANNED',
      registeredAt: '2026-01-02T00:00:00Z',
      points: 5,
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ token: 'admin-token', userId: 99, isAdmin: true })
    adminService.getAdminUsers.mockResolvedValue(SAMPLE_PAGE)
    adminService.banUser.mockResolvedValue({})
    adminService.unbanUser.mockResolvedValue({})
  })

  it('renders user rows from the query response', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
      expect(screen.getByText('Bob Banned')).toBeInTheDocument()
    })
  })

  it('confirms and bans an active user', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.click(screen.getByRole('button', { name: 'Ban' }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Ban Ada Lovelace?')

    await user.click(within(dialog).getByRole('button', { name: 'Ban' }))

    await waitFor(() => {
      expect(adminService.banUser).toHaveBeenCalledWith(1, 'admin-token')
    })
  })

  it('unbans a banned user without a confirm dialog', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => screen.getByText('Bob Banned'))

    await user.click(screen.getByRole('button', { name: 'Unban' }))

    await waitFor(() => {
      expect(adminService.unbanUser).toHaveBeenCalledWith(2, 'admin-token')
    })
  })
})
