import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminCommentsPage from './AdminCommentsPage.jsx'

vi.mock('../../hooks/useAdmin.js', () => ({
  useAdminCommentsQuery: vi.fn(),
  useDeleteAdminComment: vi.fn(),
}))

import {
  useAdminCommentsQuery,
  useDeleteAdminComment,
} from '../../hooks/useAdmin.js'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <AdminCommentsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const PAGE = {
  content: [
    {
      id: 10,
      authorName: 'Mod',
      reportId: 55,
      content: 'Spam text',
      createdAt: '2026-05-01T12:00:00Z',
    },
  ],
  totalPages: 1,
}

describe('AdminCommentsPage', () => {
  const mutate = vi.fn((id, opts) => {
    opts?.onSettled?.()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    useAdminCommentsQuery.mockReturnValue({
      data: PAGE,
      isLoading: false,
      isError: false,
      error: null,
    })
    useDeleteAdminComment.mockReturnValue({ mutate, isPending: false })
  })

  it('renders comments table', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /comments/i })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Spam text')).toBeInTheDocument())
  })

  it('deletes after confirmation', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Spam text')

    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(mutate).toHaveBeenCalledWith(10, expect.any(Object)))
  })

  it('updates report id filter', async () => {
    const user = userEvent.setup()
    renderPage()
    const [reportInput] = screen.getAllByPlaceholderText('any')
    await user.clear(reportInput)
    await user.type(reportInput, '99')
    await waitFor(() =>
      expect(useAdminCommentsQuery.mock.calls.at(-1)?.[0]).toMatchObject({ reportId: '99' }),
    )
  })
})
