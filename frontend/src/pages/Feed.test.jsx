import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Feed from './Feed.jsx'

vi.mock('../hooks/useReports.js', () => ({
  useReports: vi.fn(),
}))
vi.mock('../hooks/useUserProfile.js', () => ({
  useUserProfile: () => ({ data: undefined }),
}))
// Navbar pulls in too many providers; stub it for these tests.
vi.mock('../components/Navbar.jsx', () => ({
  default: () => <nav data-testid="navbar-stub" />,
}))

import { useReports } from '../hooks/useReports.js'

function renderFeed() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Feed />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Feed page', () => {
  it('shows skeletons while loading', () => {
    useReports.mockReturnValue({ data: undefined, isPending: true, isError: false })
    const { container } = renderFeed()
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('shows the empty state when the list is empty', () => {
    useReports.mockReturnValue({ data: [], isPending: false, isError: false })
    renderFeed()
    expect(screen.getByText(/no reports yet/i)).toBeInTheDocument()
  })

  it('renders one ReportCard per report', () => {
    useReports.mockReturnValue({
      data: [
        { id: 1, title: 'A', description: 'a', date: 'x', ownerId: 1, agrees: 0, disagrees: 0, location: '0,0', objects: [], image: null, reportedBy: 'User #1' },
        { id: 2, title: 'B', description: 'b', date: 'x', ownerId: 2, agrees: 0, disagrees: 0, location: '0,0', objects: [], image: null, reportedBy: 'User #2' },
      ],
      isPending: false,
      isError: false,
    })
    renderFeed()
    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('shows the error state on fetch failure', () => {
    useReports.mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
      error: new Error('Network down'),
    })
    renderFeed()
    expect(screen.getByRole('alert')).toHaveTextContent(/network down/i)
  })
})
