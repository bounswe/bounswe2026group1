import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminReportsPage from './AdminReportsPage.jsx'

vi.mock('../../hooks/useAdmin.js', () => ({
  useAdminReportsQuery: vi.fn(),
  useChangeReportStatus: vi.fn(),
  useDeleteAdminReport: vi.fn(),
}))

import {
  useAdminReportsQuery,
  useChangeReportStatus,
  useDeleteAdminReport,
} from '../../hooks/useAdmin.js'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const ROW = {
  reportId: 100,
  categoryName: 'Ramp',
  categoryType: 'FEATURE',
  environment: 'OUTDOOR',
  status: 'PENDING',
  agrees: 2,
  disagrees: 1,
  publishDate: '2026-05-02T10:00:00Z',
  userId: 7,
}

describe('AdminReportsPage', () => {
  const statusMutate = vi.fn()
  const deleteMutate = vi.fn((id, opts) => opts?.onSettled?.())

  beforeEach(() => {
    vi.clearAllMocks()
    useAdminReportsQuery.mockReturnValue({
      data: { content: [ROW], totalPages: 1 },
      isLoading: false,
      isError: false,
      error: null,
    })
    useChangeReportStatus.mockReturnValue({ mutate: statusMutate, isPending: false })
    useDeleteAdminReport.mockReturnValue({ mutate: deleteMutate, isPending: false })
  })

  it('renders reports heading and row', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /^reports$/i })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Ramp')).toBeInTheDocument())
  })

  it('changes status via select', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    const rowStatus = screen.getByDisplayValue('PENDING')
    await user.selectOptions(rowStatus, 'VERIFIED')

    await waitFor(() =>
      expect(statusMutate).toHaveBeenCalledWith(
        { id: 100, status: 'VERIFIED' },
        expect.any(Object),
      ),
    )
  })

  it('skips status mutation when unchanged', async () => {
    useAdminReportsQuery.mockReturnValue({
      data: { content: [{ ...ROW, status: 'VERIFIED' }], totalPages: 1 },
      isLoading: false,
      isError: false,
      error: null,
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.selectOptions(screen.getByDisplayValue('VERIFIED'), 'VERIFIED')
    expect(statusMutate).not.toHaveBeenCalled()
  })

  it('opens delete confirm and deletes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(deleteMutate).toHaveBeenCalledWith(100, expect.any(Object)))
  })

  it('updates environment filter in query params', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.selectOptions(screen.getByLabelText(/^environment$/i), 'INDOOR')

    await waitFor(() =>
      expect(useAdminReportsQuery.mock.calls.at(-1)[0]).toMatchObject({
        environment: 'INDOOR',
        page: 0,
      }),
    )
  })

  it('updates category id filter', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    const categoryInput = screen.getByPlaceholderText('any')
    await user.clear(categoryInput)
    await user.type(categoryInput, '12')

    await waitFor(() =>
      expect(useAdminReportsQuery.mock.calls.at(-1)[0]).toMatchObject({
        categoryId: '12',
        page: 0,
      }),
    )
  })

  it('updates status filter', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.selectOptions(screen.getByLabelText(/^status$/i), 'VERIFIED')

    await waitFor(() =>
      expect(useAdminReportsQuery.mock.calls.at(-1)[0]).toMatchObject({
        status: 'VERIFIED',
        page: 0,
      }),
    )
  })

  it('updates type filter', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.selectOptions(screen.getByLabelText(/^type$/i), 'OBSTACLE')

    await waitFor(() =>
      expect(useAdminReportsQuery.mock.calls.at(-1)[0]).toMatchObject({
        type: 'OBSTACLE',
        page: 0,
      }),
    )
  })

  it('shows status mutation errors', async () => {
    statusMutate.mockImplementation((_payload, opts) => {
      opts?.onError?.(new Error('cannot verify'))
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.selectOptions(screen.getByDisplayValue('PENDING'), 'VERIFIED')

    expect(await screen.findByText(/cannot verify/i)).toBeInTheDocument()
  })

  it('shows delete mutation errors', async () => {
    deleteMutate.mockImplementation((_id, opts) => {
      opts?.onError?.(new Error('cannot delete'))
      opts?.onSettled?.()
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Ramp')

    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText(/cannot delete/i)).toBeInTheDocument()
  })

  it('shows loading state', () => {
    useAdminReportsQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    })
    renderPage()
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('shows query errors', () => {
    useAdminReportsQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('failed query'),
    })
    renderPage()
    expect(screen.getByText(/failed query/i)).toBeInTheDocument()
  })

  it('renders obstacle rows with the neutral badge tone', async () => {
    useAdminReportsQuery.mockReturnValue({
      data: {
        content: [{ ...ROW, categoryType: 'OBSTACLE' }],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
      error: null,
    })
    renderPage()
    await screen.findByText('OBSTACLE')
  })
})
