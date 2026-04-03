import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext.jsx'
import ReportPanel from './ReportPanel.jsx'
import * as reportService from '../services/reportService.js'

vi.mock('../services/reportService.js')

const MOCK_REPORT = {
  id: 1,
  title: 'Broken Elevator',
  description: 'The elevator is out of service.',
  status: 'unverified',
  date: '4 April 2026',
  location: '41.0683, 29.0505',
  reportedBy: 'User #1',
  agrees: 3,
  disagrees: 1,
  tags: ['Broken Elevator'],
  image: null,
  latitude: 41.0683,
  longitude: 29.0505,
}

function renderPanel({ report = MOCK_REPORT, token = null, onClose = vi.fn(), onVoteUpdate = vi.fn() } = {}) {
  if (token) localStorage.setItem('token', token)
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ReportPanel report={report} onClose={onClose} onVoteUpdate={onVoteUpdate} />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('ReportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ─── Rendering ───────────────────────────────────────────────────────────────

  describe('rendering', () => {
    it('renders the report title', () => {
      renderPanel()
      expect(screen.getByRole('heading', { name: 'Broken Elevator' })).toBeInTheDocument()
    })

    it('renders the description', () => {
      renderPanel()
      expect(screen.getByText('The elevator is out of service.')).toBeInTheDocument()
    })

    it('renders the location', () => {
      renderPanel()
      expect(screen.getByText('41.0683, 29.0505')).toBeInTheDocument()
    })

    it('renders Agree and Disagree buttons', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: 'Agree' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Disagree' })).toBeInTheDocument()
    })

    it('renders the close button', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /close panel/i })).toBeInTheDocument()
    })

    it('renders nothing when report is null', () => {
      const { container } = renderPanel({ report: null })
      expect(container).toBeEmptyDOMElement()
    })

    it('shows image placeholder when no image', () => {
      renderPanel()
      expect(screen.getByText('image', { selector: '.material-symbols-outlined' })).toBeInTheDocument()
    })

    it('shows image when report has one', () => {
      renderPanel({ report: { ...MOCK_REPORT, image: 'https://example.com/img.jpg' } })
      expect(screen.getByRole('img', { name: /broken elevator/i })).toHaveAttribute('src', 'https://example.com/img.jpg')
    })
  })

  // ─── Close button ─────────────────────────────────────────────────────────────

  describe('close button', () => {
    it('calls onClose when close button is clicked', async () => {
      const onClose = vi.fn()
      const user = userEvent.setup()
      renderPanel({ onClose })
      await user.click(screen.getByRole('button', { name: /close panel/i }))
      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })

  // ─── Vote button colors ───────────────────────────────────────────────────────

  describe('vote button colors', () => {
    it('agree button starts with gray background', () => {
      renderPanel()
      const agreeBtn = screen.getByRole('button', { name: 'Agree' })
      expect(agreeBtn.className).toContain('bg-surface-container-highest')
    })

    it('disagree button starts with gray background', () => {
      renderPanel()
      const disagreeBtn = screen.getByRole('button', { name: 'Disagree' })
      expect(disagreeBtn.className).toContain('bg-surface-container-highest')
    })

    it('agree button turns green after clicking agree', async () => {
      // Use a valid-looking JWT with id claim
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 4 })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 4 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-primary')
      })
    })

    it('disagree button turns red after clicking disagree', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 2 })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, disagrees: 2 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken })

      await user.click(screen.getByRole('button', { name: 'Disagree' }))

      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-error')
      })
    })
  })

  // ─── Vote error handling ──────────────────────────────────────────────────────

  describe('vote error handling', () => {
    it('shows error when voting without being logged in', async () => {
      const user = userEvent.setup()
      renderPanel() // no token
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      expect(screen.getByText(/must be logged in/i)).toBeInTheDocument()
    })

    it('shows error when vote API call fails', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      reportService.agreeReport.mockRejectedValue(new Error('Server error'))
      const user = userEvent.setup()
      renderPanel({ token: fakeToken })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(screen.getByText(/failed to submit vote/i)).toBeInTheDocument()
      })
    })
  })
})
