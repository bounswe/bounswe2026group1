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

function renderPanel({
  report = MOCK_REPORT,
  token = null,
  userVote = null,
  onClose = vi.fn(),
  onVoteUpdate = vi.fn(),
  onVoteChange = vi.fn(),
} = {}) {
  if (token) localStorage.setItem('token', token)
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ReportPanel
          report={report}
          userVote={userVote}
          onClose={onClose}
          onVoteUpdate={onVoteUpdate}
          onVoteChange={onVoteChange}
        />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('ReportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    reportService.getCommentsByReport.mockResolvedValue([])
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

  // ─── Vote button colors and behavior ─────────────────────────────────────────
  describe('vote button colors', () => {
    it('agree button starts with gray background when userVote is null', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-surface-container-highest')
    })

    it('disagree button starts with gray background when userVote is null', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-surface-container-highest')
    })

    it('agree button is green when userVote prop is agree', () => {
      renderPanel({ userVote: 'agree' })
      expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-primary')
      expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-surface-container-highest')
    })

    it('disagree button is red when userVote prop is disagree', () => {
      renderPanel({ userVote: 'disagree' })
      expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-error')
      expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-surface-container-highest')
    })

    it('calls onVoteChange with agree when agree vote is cast', async () => {
      const fakeToken = 'fake-token'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 4 })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 4 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith('agree')
      })
    })

    it('calls onVoteChange with disagree when disagree vote is cast', async () => {
      const fakeToken = 'fake-token'
      const onVoteChange = vi.fn()
      reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 2 })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, disagrees: 2 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Disagree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith('disagree')
      })
    })

    it('calls onVoteChange with null when toggling off an existing agree vote', async () => {
      const fakeToken = 'fake-token'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 2 })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 2 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, userVote: 'agree', onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith(null)
      })
    })
  })

  // ─── Vote error handling ──────────────────────────────────────────────────────
  describe('vote error handling', () => {
    it('navigates to /login when voting without being logged in', async () => {
      const user = userEvent.setup()
      renderPanel() // no token
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      expect(screen.queryByText(/failed to submit vote/i)).not.toBeInTheDocument()
    })

    it('shows error when vote API call fails', async () => {
      const fakeToken = 'fake-token'
      reportService.agreeReport.mockRejectedValue(new Error('Server error'))
      const user = userEvent.setup()
      renderPanel({ token: fakeToken })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(screen.getByText(/failed to submit vote/i)).toBeInTheDocument()
      })
    })
  })

  // ─── Follow/Unfollow tests ───────────────────────────────────────────────────
  describe('follow/unfollow button', () => {
    it('toggles follow state when clicked', async () => {
      const user = userEvent.setup()
      renderPanel({ token: 'fake-token' })

      const button = screen.getByRole('button', { name: /Follow Updates/i })
      expect(button).toHaveTextContent('Follow Updates')

      await user.click(button)
      expect(button).toHaveTextContent('Unfollow')

      await user.click(button)
      expect(button).toHaveTextContent('Follow Updates')
    })

    it('does not toggle follow if unauthenticated', async () => {
      const user = userEvent.setup()
      renderPanel({ token: null })

      const button = screen.getByRole('button', { name: /Follow Updates/i })
      await user.click(button)
      expect(button).toHaveTextContent('Follow Updates')
    })
  })

  // ─── Comments tests ──────────────────────────────────────────────────────────
  describe('comments', () => {
    beforeEach(() => {
      reportService.createComment.mockImplementation((reportId, content) =>
        Promise.resolve({ id: 'c2', content, author: { id: 'user123', name: 'Tester' }, createdAt: new Date().toISOString() })
      )
      reportService.deleteComment.mockResolvedValue({})
      reportService.getCommentsByReport.mockResolvedValue([
        { id: 'c1', content: 'Existing comment', author: { id: 'user123', name: 'Tester' }, createdAt: '2026-04-07T12:00:00Z' }
      ])
    })

    it('can submit a new comment', async () => {
      const user = userEvent.setup()
      renderPanel({ token: 'fake-token' })

      const textarea = screen.getByPlaceholderText('Add a comment...')
      await user.type(textarea, 'New comment')
      await user.click(screen.getByText('Post'))

      await waitFor(() => expect(screen.getByText('New comment')).toBeInTheDocument())
    })

    it('can delete own comment', async () => {
      const user = userEvent.setup()
      renderPanel({ token: 'fake-token' })

      await waitFor(() => screen.getByText('Existing comment'))

      const deleteBtn = screen.getByLabelText('Delete comment')
      await user.click(deleteBtn)

      await waitFor(() => expect(screen.queryByText('Existing comment')).not.toBeInTheDocument())
    })
  })
})