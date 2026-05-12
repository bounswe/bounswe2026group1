import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminUsersPage from './AdminUsersPage.jsx'

vi.mock('../../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../../components/admin/CreateUserModal.jsx', () => ({
  default: ({ open, onClose }) =>
    open ? (
      <div data-testid="create-user-modal">
        <button type="button" onClick={onClose}>
          Close modal
        </button>
      </div>
    ) : null,
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
    adminService.changeUserRole.mockResolvedValue({})
    adminService.deleteAdminUser.mockResolvedValue({})
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

  it('opens create-user modal from header action', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.click(screen.getByRole('button', { name: /new user/i }))
    expect(screen.getByTestId('create-user-modal')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /close modal/i }))
    expect(screen.queryByTestId('create-user-modal')).not.toBeInTheDocument()
  })

  it('filters by status and role via query params', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.selectOptions(screen.getByLabelText(/^status$/i), 'ACTIVE')
    await waitFor(() =>
      expect(adminService.getAdminUsers).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'ACTIVE', page: 0 }),
        'admin-token',
      ),
    )

    await user.selectOptions(screen.getByLabelText(/^role$/i), 'ADMIN')
    await waitFor(() =>
      expect(adminService.getAdminUsers).toHaveBeenCalledWith(
        expect.objectContaining({ role: 'ADMIN', page: 0 }),
        'admin-token',
      ),
    )
  })

  it('changes page through pagination', async () => {
    const user = userEvent.setup()
    adminService.getAdminUsers.mockResolvedValue({
      ...SAMPLE_PAGE,
      totalPages: 3,
      totalElements: 60,
    })
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.click(screen.getByRole('button', { name: /next/i }))
    await waitFor(() =>
      expect(adminService.getAdminUsers).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 }),
        'admin-token',
      ),
    )
  })

  it('confirms role change and calls changeUserRole', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.click(screen.getAllByRole('button', { name: /^make admin$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^make admin$/i }))

    await waitFor(() =>
      expect(adminService.changeUserRole).toHaveBeenCalledWith(1, 'ADMIN', 'admin-token'),
    )
  })

  it('confirms delete and calls deleteAdminUser', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i })
    await user.click(deleteButtons[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() =>
      expect(adminService.deleteAdminUser).toHaveBeenCalledWith(1, 'admin-token'),
    )
  })

  it('disables banning yourself', async () => {
    useAuth.mockReturnValue({ token: 'admin-token', userId: 1, isAdmin: true })
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    const banAda = screen.getAllByRole('button', { name: /^ban$/i })[0]
    expect(banAda).toBeDisabled()
    expect(banAda).toHaveAttribute('title', "You can't ban yourself")
  })

  it('shows mutation errors from ban failures', async () => {
    const user = userEvent.setup()
    adminService.banUser.mockRejectedValueOnce(new Error('Rate limited'))
    renderPage()
    await waitFor(() => screen.getByText('Ada Lovelace'))

    await user.click(screen.getAllByRole('button', { name: /^ban$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^ban$/i }))

    expect(await screen.findByText(/rate limited/i)).toBeInTheDocument()
  })
})
