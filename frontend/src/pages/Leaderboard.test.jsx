import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Leaderboard from './Leaderboard.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../hooks/useLeaderboard.js', () => ({
  useLeaderboard: vi.fn(),
  leaderboardKey: () => ['leaderboard'],
}))
vi.mock('../components/Navbar.jsx', () => ({
  default: () => <div data-testid="navbar" />,
}))

import { useAuth } from '../context/AuthContext.jsx'
import { useLeaderboard } from '../hooks/useLeaderboard.js'

function renderPage() {
  return render(
    <MemoryRouter>
      <Leaderboard />
    </MemoryRouter>,
  )
}

const SAMPLE_PAYLOAD = {
  entries: [
    { rank: 1, userId: 10, name: 'Alice', avatarUrl: null, points: 200 },
    { rank: 1, userId: 11, name: 'Bob', avatarUrl: null, points: 200 },
    { rank: 3, userId: 12, name: 'Carol', avatarUrl: null, points: 150 },
  ],
  callerRank: { rank: 5, points: 95 },
}

describe('Leaderboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ token: 'tok', isAuthenticated: true })
  })

  it('renders a loading state while the query is pending', () => {
    useLeaderboard.mockReturnValue({ data: undefined, isPending: true, isError: false })
    renderPage()
    expect(screen.getByText(/loading leaderboard/i)).toBeInTheDocument()
  })

  it('renders an error state when the query fails', () => {
    useLeaderboard.mockReturnValue({ data: undefined, isPending: false, isError: true })
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/failed to load/i)
  })

  it('renders the entries with rank, name, and points', () => {
    useLeaderboard.mockReturnValue({ data: SAMPLE_PAYLOAD, isPending: false, isError: false })
    renderPage()

    const list = screen.getByRole('list')
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(3)

    // Tied users (Alice, Bob at rank 1) both show "#1"; Carol at rank 3.
    expect(within(items[0]).getByText('#1')).toBeInTheDocument()
    expect(within(items[1]).getByText('#1')).toBeInTheDocument()
    expect(within(items[2]).getByText('#3')).toBeInTheDocument()

    expect(within(items[0]).getByText('Alice')).toBeInTheDocument()
    expect(within(items[2]).getByText('150')).toBeInTheDocument()
  })

  it('shows the "your rank" banner when authenticated and ranked', () => {
    useLeaderboard.mockReturnValue({ data: SAMPLE_PAYLOAD, isPending: false, isError: false })
    renderPage()

    // Banner sits above the list and surfaces both rank and points.
    expect(screen.getByText(/your rank/i)).toBeInTheDocument()
    expect(screen.getByText('#5')).toBeInTheDocument()
    expect(screen.getByText('95')).toBeInTheDocument()
  })

  it('hides the "your rank" banner when callerRank is null (anonymous or opt-out)', () => {
    useAuth.mockReturnValue({ token: null, isAuthenticated: false })
    useLeaderboard.mockReturnValue({
      data: { ...SAMPLE_PAYLOAD, callerRank: null },
      isPending: false,
      isError: false,
    })
    renderPage()

    expect(screen.queryByText(/your rank/i)).not.toBeInTheDocument()
    // Anonymous viewers get a hint to log in instead.
    expect(screen.getByText(/log in/i)).toBeInTheDocument()
  })

  it('shows an empty-state message when nobody is ranked yet', () => {
    useLeaderboard.mockReturnValue({
      data: { entries: [], callerRank: null },
      isPending: false,
      isError: false,
    })
    renderPage()
    expect(screen.getByText(/no ranked users yet/i)).toBeInTheDocument()
  })

  it('links each entry to the user profile page', () => {
    useLeaderboard.mockReturnValue({ data: SAMPLE_PAYLOAD, isPending: false, isError: false })
    renderPage()

    const aliceLink = screen.getByRole('link', { name: 'Alice' })
    expect(aliceLink).toHaveAttribute('href', '/profile/10')
  })
})
