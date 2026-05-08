import { render, screen, waitFor, act, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReportPanel from './ReportPanel.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => ({ token: 'mock-token', isAuthenticated: true, userId: 'user123' }),
}))

vi.mock('../services/reportService.js', () => ({
  agreeReport: vi.fn(),
  disagreeReport: vi.fn(),
  agreeFixRequest: vi.fn(),
  disagreeFixRequest: vi.fn(),
  mapReport: vi.fn(r => r),
  mapFixRequest: vi.fn(fr => fr),
  getCommentsByReport: vi.fn(() => Promise.resolve([])),
  createComment: vi.fn(),
  deleteComment: vi.fn(() => Promise.resolve()),
  getFollowStatus: vi.fn(() => Promise.resolve({ following: false })),
  followReport: vi.fn(() => Promise.resolve({ following: true })),
  unfollowReport: vi.fn(() => Promise.resolve({ following: false })),
}))

import {
  agreeReport,
  disagreeReport,
  agreeFixRequest,
  getCommentsByReport,
  createComment,
  deleteComment,
} from '../services/reportService.js'

describe('ReportPanel', () => {
  let onCloseMock, onVoteChangeMock, onFollowChangeMock, onVoteUpdateMock, user

  const report = {
    id: 'r1',
    title: 'Broken Elevator',
    description: 'The elevator is out of service.',
    location: '41.0683, 29.0505',
    agrees: 0,
    disagrees: 0,
    imageUrl: null,
    userVote: null,
    isFollowed: false,
  }

  const existingComment = {
    id: 'c1',
    content: 'Existing comment',
    author: { id: 'user123', name: 'Tester' },
  }

  beforeEach(() => {
    onCloseMock = vi.fn()
    onVoteChangeMock = vi.fn()
    onFollowChangeMock = vi.fn()
    onVoteUpdateMock = vi.fn()
    user = userEvent.setup()
    vi.clearAllMocks()
    getCommentsByReport.mockResolvedValue([])
  })

  function renderPanel(props = {}) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReportPanel
            report={report}
            onClose={onCloseMock}
            onVoteChange={onVoteChangeMock}
            onVoteUpdate={onVoteUpdateMock}
            onFollowChange={onFollowChangeMock}
            {...props}
          />
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  test('renders the report title, description, location and buttons', async () => {
    renderPanel()

    expect(screen.getByText(report.title)).toBeInTheDocument()
    expect(screen.getByText(report.description)).toBeInTheDocument()
    expect(screen.getByText(/41\.0683, 29\.0505/)).toBeInTheDocument()
    expect(screen.getByLabelText('Agree')).toBeInTheDocument()
    expect(screen.getByLabelText('Disagree')).toBeInTheDocument()
  })

  test('calls onVoteChange with agree/disagree/null correctly', async () => {
    agreeReport
      .mockResolvedValueOnce({ id: 'r1', agrees: 1, disagrees: 0, userVote: 'agree' })
      .mockResolvedValueOnce({ id: 'r1', agrees: 0, disagrees: 0, userVote: null })
    disagreeReport.mockResolvedValueOnce({ id: 'r1', agrees: 0, disagrees: 1, userVote: 'disagree' })

    renderPanel()

    const agreeBtn = screen.getByLabelText('Agree')
    const disagreeBtn = screen.getByLabelText('Disagree')

    await act(async () => { await user.click(agreeBtn) })
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith('agree'))

    await act(async () => { await user.click(disagreeBtn) })
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith('disagree'))

    await act(async () => { await user.click(agreeBtn) })
    await waitFor(() => expect(onVoteChangeMock).toHaveBeenCalledWith(null))
  })

  test('toggles follow state', async () => {
    renderPanel()

    const followBtn = screen.getByRole('button', { name: /follow/i })
    await act(async () => { await user.click(followBtn) })

    await waitFor(() => {
      expect(onFollowChangeMock).toHaveBeenCalled()
      expect(followBtn.textContent.toLowerCase()).toMatch(/unfollow/)
    })
  })

  test('can delete own comment', async () => {
    getCommentsByReport.mockResolvedValueOnce([existingComment])

    renderPanel()

    const comment = await screen.findByText((content, element) =>
      element.tagName.toLowerCase() === 'p' && content.includes('Existing comment')
    )

    const deleteBtn = within(comment.parentElement).getByLabelText('Delete comment')

    await act(async () => { await user.click(deleteBtn) })

    await waitFor(() => {
      expect(comment).not.toBeInTheDocument()
    })
  })

  // ── fix-flow additions ──────────────────────────────────────────────────

  test('shows the fix CTA for an authenticated user when no active fix exists', () => {
    renderPanel()
    expect(screen.getByText(/Has this been fixed\?/i)).toBeInTheDocument()
  })

  test('hides the fix CTA when the report is already FIXED', () => {
    renderPanel({ report: { ...report, status: 'fixed' } })
    expect(screen.queryByText(/Has this been fixed\?/i)).not.toBeInTheDocument()
    expect(screen.getByText('Fixed')).toBeInTheDocument()
  })

  test('hides the fix CTA when an active fix request is already in flight', () => {
    const activeFix = {
      id: 11,
      submittedByUserId: 99, // different user
      submittedByName: 'Mehmet',
      description: null,
      state: 'OPEN',
      agrees: 0,
      disagrees: 0,
      createdAt: '2026-05-01T10:00:00Z',
      mediaUrls: [],
      userVote: null,
    }
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })
    expect(screen.queryByText(/Has this been fixed\?/i)).not.toBeInTheDocument()
    expect(screen.getByText(/Fix Requested/i)).toBeInTheDocument()
    expect(screen.getByText(/Fix pending/i)).toBeInTheDocument()
  })

  test('renders the active fix card with vote buttons for non-submitter', () => {
    const activeFix = {
      id: 11,
      submittedByUserId: 99,
      submittedByName: 'Mehmet',
      description: 'New ramp installed',
      state: 'OPEN',
      agrees: 3,
      disagrees: 1,
      createdAt: '2026-05-01T10:00:00Z',
      mediaUrls: [],
      userVote: null,
    }
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })

    expect(screen.getByText('New ramp installed')).toBeInTheDocument()
    expect(screen.getByText(/3 agrees/i)).toBeInTheDocument()
    expect(screen.getByText(/1 disagrees/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Yes, fixed/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /No, still there/i })).toBeInTheDocument()
  })

  test('hides the vote buttons when the current user submitted the fix', () => {
    const activeFix = {
      id: 11,
      submittedByUserId: 'user123', // matches the mocked auth userId
      submittedByName: 'You',
      description: null,
      state: 'OPEN',
      agrees: 0,
      disagrees: 0,
      createdAt: '2026-05-01T10:00:00Z',
      mediaUrls: [],
      userVote: null,
    }
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })

    expect(screen.queryByRole('button', { name: /Yes, fixed/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /No, still there/i })).not.toBeInTheDocument()
    expect(screen.getByText(/You submitted this fix report/i)).toBeInTheDocument()
  })

  test('clicking Yes, fixed calls agreeFixRequest', async () => {
    const activeFix = {
      id: 11,
      submittedByUserId: 99,
      submittedByName: 'Mehmet',
      description: null,
      state: 'OPEN',
      agrees: 0,
      disagrees: 0,
      createdAt: '2026-05-01T10:00:00Z',
      mediaUrls: [],
      userVote: null,
    }
    agreeFixRequest.mockResolvedValue({ ...activeFix, agrees: 1, userVote: 'AGREE' })
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })

    await act(async () => { await user.click(screen.getByRole('button', { name: /Yes, fixed/i })) })

    await waitFor(() => {
      expect(agreeFixRequest).toHaveBeenCalledWith('r1', 11, 'mock-token')
    })
  })
})