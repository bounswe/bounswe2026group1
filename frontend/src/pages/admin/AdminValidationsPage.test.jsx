import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminValidationsPage from './AdminValidationsPage.jsx'

vi.mock('../../hooks/useAdmin.js', () => ({
  useAdminValidationsQuery: vi.fn(),
  useDeleteAdminValidation: vi.fn(),
}))

import {
  useAdminValidationsQuery,
  useDeleteAdminValidation,
} from '../../hooks/useAdmin.js'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <AdminValidationsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminValidationsPage', () => {
  const mutate = vi.fn((id, opts) => opts?.onSettled?.())

  beforeEach(() => {
    vi.clearAllMocks()
    useAdminValidationsQuery.mockReturnValue({
      data: {
        content: [
          { id: 5, userName: 'Voter', reportId: 9, voteType: 'AGREE' },
          { id: 6, userName: 'Other', reportId: 9, voteType: 'DISAGREE' },
        ],
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
      error: null,
    })
    useDeleteAdminValidation.mockReturnValue({ mutate, isPending: false })
  })

  it('renders validations and vote badges', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /^validations$/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('AGREE')).toBeInTheDocument()
      expect(screen.getByText('DISAGREE')).toBeInTheDocument()
    })
  })

  it('deletes from confirm dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Voter')

    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i })
    await user.click(deleteButtons[0])

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(mutate).toHaveBeenCalledWith(5, expect.any(Object)))
  })

  it('filters by vote type', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Voter')

    await user.selectOptions(screen.getByLabelText(/^vote$/i), 'AGREE')

    await waitFor(() =>
      expect(useAdminValidationsQuery.mock.calls.at(-1)[0]).toMatchObject({
        voteType: 'AGREE',
        page: 0,
      }),
    )
  })

  it('filters by report id', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Voter')

    const input = screen.getByLabelText(/^report id$/i)
    await user.clear(input)
    await user.type(input, '42')

    await waitFor(() =>
      expect(useAdminValidationsQuery.mock.calls.at(-1)[0]).toMatchObject({
        reportId: '42',
        page: 0,
      }),
    )
  })

  it('shows delete errors', async () => {
    mutate.mockImplementation((_id, opts) => {
      opts?.onError?.(new Error('still referenced'))
      opts?.onSettled?.()
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Voter')

    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText(/still referenced/i)).toBeInTheDocument()
  })

  it('shows loading state', () => {
    useAdminValidationsQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    })
    renderPage()
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('shows query errors', () => {
    useAdminValidationsQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('boom'),
    })
    renderPage()
    expect(screen.getByText(/boom/i)).toBeInTheDocument()
  })
})
