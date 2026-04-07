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
  currentUser = null,
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
          currentUser={currentUser}
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

  // ─── Rendering ───────────────────────────────────────────────
  describe('rendering', () => {
    it('renders the report title', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByRole('heading', { name: 'Broken Elevator' })).toBeInTheDocument())
    })

    it('renders the description', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByText('The elevator is out of service.')).toBeInTheDocument())
    })

    it('renders the location', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByText('41.0683, 29.0505')).toBeInTheDocument())
    })

    it('renders Agree and Disagree buttons', async () => {
      renderPanel()
      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Agree' })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Disagree' })).toBeInTheDocument()
      })
    })

    it('renders the close button', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByRole('button', { name: /close panel/i })).toBeInTheDocument())
    })

    it('renders nothing when report is null', () => {
      const { container } = renderPanel({ report: null })
      expect(container).toBeEmptyDOMElement()
    })

    it('shows image placeholder when no image', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByText('image', { selector: '.material-symbols-outlined' })).toBeInTheDocument())
    })

    it('shows image when report has one', async () => {
      renderPanel({ report: { ...MOCK_REPORT, image: 'https://example.com/img.jpg' } })
      await waitFor(() =>
        expect(screen.getByRole('img', { name: /broken elevator/i })).toHaveAttribute(
          'src',
          'https://example.com/img.jpg'
        )
      )
    })
  })

  // ─── Close button ─────────────────────────────────────────────
  describe('close button', () => {
    it('calls onClose when close button is clicked', async () => {
      const onClose = vi.fn()
      const user = userEvent.setup()
      renderPanel({ onClose })
      await user.click(screen.getByRole('button', { name: /close panel/i }))
      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })

  // ─── Vote button colors ───────────────────────────────────────
  describe('vote button colors', () => {
    it('agree button starts with gray background when userVote is null', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-surface-container-highest'))
    })

    it('disagree button starts with gray background when userVote is null', async () => {
      renderPanel()
      await waitFor(() => expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-surface-container-highest'))
    })

    it('agree button is green when userVote prop is agree', async () => {
      renderPanel({ userVote: 'agree' })
      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-primary')
        expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-surface-container-highest')
      })
    })

    it('disagree button is red when userVote prop is disagree', async () => {
      renderPanel({ userVote: 'disagree' })
      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Disagree' }).className).toContain('bg-error')
        expect(screen.getByRole('button', { name: 'Agree' }).className).toContain('bg-surface-container-highest')
      })
    })

    it('calls onVoteChange with agree when agree vote is cast', async () => {
      const fakeToken = 'fake-jwt'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 4 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      await waitFor(() => expect(onVoteChange).toHaveBeenCalledWith('agree'))
    })

    it('calls onVoteChange with disagree when disagree vote is cast', async () => {
      const fakeToken = 'fake-jwt'
      const onVoteChange = vi.fn()
      reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 2 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })
      await user.click(screen.getByRole('button', { name: 'Disagree' }))
      await waitFor(() => expect(onVoteChange).toHaveBeenCalledWith('disagree'))
    })

    it('calls onVoteChange with null when toggling off an existing agree vote', async () => {
      const fakeToken = 'fake-jwt'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 2 })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, userVote: 'agree', onVoteChange })
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      await waitFor(() => expect(onVoteChange).toHaveBeenCalledWith(null))
    })
  })

  // ─── Vote error handling ──────────────────────────────────────
  describe('vote error handling', () => {
    it('navigates to /login when voting without being logged in', async () => {
      const user = userEvent.setup()
      renderPanel() // no token
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      expect(screen.queryByText(/failed to submit vote/i)).not.toBeInTheDocument()
    })

    it('shows error when vote API call fails', async () => {
      const fakeToken = 'fake-jwt'
      reportService.agreeReport.mockRejectedValue(new Error('Server error'))
      const user = userEvent.setup()
      renderPanel({ token: fakeToken })
      await user.click(screen.getByRole('button', { name: 'Agree' }))
      await waitFor(() => expect(screen.getByText(/failed to submit vote/i)).toBeInTheDocument())
    })
  })

  // ─── Follow / Unfollow ───────────────────────────────────────
  describe('follow/unfollow', () => {
    it('toggles follow state when clicked', async () => {
      const user = userEvent.setup()
      renderPanel({ token: 'fake-jwt' })
      const followBtn = screen.getByRole('button', { name: /follow updates/i })
      await user.click(followBtn)
      await waitFor(() => expect(followBtn).toHaveTextContent(/unfollow updates/i))
      await user.click(followBtn)
      await waitFor(() => expect(followBtn).toHaveTextContent(/follow updates/i))
    })

    it('does not toggle follow if unauthenticated', async () => {
      const user = userEvent.setup()
      renderPanel()
      const followBtn = screen.getByRole('button', { name: /follow updates/i })
      await user.click(followBtn)
      expect(followBtn).toHaveTextContent(/follow updates/i)
    })
  })

  // ─── Comments ───────────────────────────────────────────────
  describe('comments', () => {
    it('can submit a new comment', async () => {
      const user = userEvent.setup()
      const MOCK_USER = { id: 'user123', name: 'Tester' }
      const token = 'fake-jwt'
      const newComment = { id: 'c2', content: 'New comment', author: MOCK_USER, createdAt: new Date().toISOString() }
      reportService.createComment.mockResolvedValue(newComment)

      renderPanel({ token, currentUser: MOCK_USER })

      const textarea = screen.getByPlaceholderText(/add a comment/i)
      await user.type(textarea, 'New comment')
      const postBtn = screen.getByRole('button', { name: /post/i })
      await user.click(postBtn)

      await waitFor(() => screen.getByText('New comment'))
    })

    it('can delete own comment', async () => {
      const user = userEvent.setup()
      const MOCK_USER = { id: 'user123', name: 'Tester' }
      const token = 'fake-jwt'

      const reportWithComment = {
        ...MOCK_REPORT,
        comments: [
          { id: 'c1', content: 'Existing comment', author: MOCK_USER, createdAt: new Date().toISOString() },
        ],
      }

      reportService.deleteComment.mockResolvedValue({ success: true })

      renderPanel({ token, report: reportWithComment, currentUser: MOCK_USER })

      await waitFor(() => screen.getByText('Existing comment'))
      const deleteBtn = screen.getByLabelText('Delete comment')
      await user.click(deleteBtn)

      await waitFor(() => expect(reportService.deleteComment).toHaveBeenCalledWith('c1', token))
      await waitFor(() => expect(screen.queryByText('Existing comment')).not.toBeInTheDocument())
    })
  })
})