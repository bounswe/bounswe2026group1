import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import UserSearchPage from './UserSearchPage.jsx'

vi.mock('../hooks/useUserSearch.js', () => ({
  USER_SEARCH_MIN_LENGTH: 2,
  useUserSearch: vi.fn(),
}))
vi.mock('../components/Navbar.jsx', () => ({
  default: () => <nav>Nav</nav>,
}))

import { useUserSearch } from '../hooks/useUserSearch.js'

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <UserSearchPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('UserSearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUserSearch.mockReturnValue({
      data: undefined,
      isPending: false,
      isFetching: false,
      isError: false,
      error: null,
    })
  })

  it('shows hint when query is too short', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.type(screen.getByLabelText(/search users/i), 'a')
    expect(screen.getByText(/type at least 2 characters/i)).toBeInTheDocument()
  })

  it('shows searching state when query is long enough', async () => {
    const user = userEvent.setup()
    useUserSearch.mockReturnValue({
      data: undefined,
      isPending: true,
      isFetching: true,
      isError: false,
      error: null,
    })
    renderPage()
    await user.type(screen.getByLabelText(/search users/i), 'al')
    expect(screen.getByText(/searching/i)).toBeInTheDocument()
  })

  it('shows error message', () => {
    useUserSearch.mockReturnValue({
      data: undefined,
      isPending: false,
      isFetching: false,
      isError: true,
      error: new Error('Down'),
    })
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/down/i)
  })

  it('lists users and pagination when multiple pages', () => {
    useUserSearch.mockReturnValue({
      data: {
        content: [
          {
            id: 1,
            name: 'Ada',
            role: 'USER',
            contributionStats: { reportsSubmitted: 2, routesPlanned: 1 },
          },
        ],
        totalElements: 20,
        totalPages: 3,
        last: false,
      },
      isPending: false,
      isFetching: false,
      isError: false,
      error: null,
    })
    renderPage()
    expect(screen.getByText('Ada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument()
  })
})
