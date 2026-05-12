import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MyReportsSection from './MyReportsSection.jsx'

vi.mock('./MyReportDetailModal.jsx', () => ({
  default: ({ report, onClose }) => (
    <div data-testid="detail-modal">
      <span>Modal for {report.id}</span>
      <button type="button" onClick={onClose}>
        Close modal
      </button>
    </div>
  ),
}))

vi.mock('../hooks/useUserReports.js', () => ({
  useUserReports: vi.fn(),
}))

import { useUserReports } from '../hooks/useUserReports.js'

function renderSection(props = {}) {
  const qc = new QueryClient()
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <MyReportsSection userId={1} {...props} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('MyReportsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading', () => {
    useUserReports.mockReturnValue({ data: undefined, isPending: true, isError: false, error: null })
    renderSection()
    expect(screen.getByText(/loading your reports/i)).toBeInTheDocument()
  })

  it('shows error with message', () => {
    useUserReports.mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
      error: { message: 'nope' },
    })
    renderSection()
    expect(screen.getByRole('alert')).toHaveTextContent(/nope/)
  })

  it('links to map for own empty profile', () => {
    useUserReports.mockReturnValue({ data: [], isPending: false, isError: false, error: null })
    renderSection({ isOwnProfile: true })
    expect(screen.getByRole('link', { name: /add one on the map/i })).toHaveAttribute('href', '/')
  })

  it('lists reports and opens detail modal', async () => {
    const user = userEvent.setup()
    useUserReports.mockReturnValue({
      data: [
        {
          id: 5,
          title: 'Crack in sidewalk',
          description: 'Long text '.repeat(30),
          status: 'unverified',
          date: 'May 2026',
          image: null,
        },
      ],
      isPending: false,
      isError: false,
      error: null,
    })
    renderSection()

    expect(screen.getByText('Crack in sidewalk')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /crack in sidewalk/i }))
    expect(screen.getByTestId('detail-modal')).toHaveTextContent('Modal for 5')
  })
})
