import { render, screen, waitFor, act, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReportPanel from './ReportPanel.jsx'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location-probe">{location.pathname}</div>
}

// Mutable auth state so individual tests can flip isAdmin / userId without
// resetting the whole module mock. Default: signed-in non-admin "user123".
const mockAuth = { token: 'mock-token', isAuthenticated: true, userId: 'user123', isAdmin: false }
vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => mockAuth,
}))

vi.mock('../hooks/useUserProfile.js', () => ({
  useUserProfile: vi.fn(),
}))

// CreateReportPanel is a heavyweight component; stub it so ReportPanel tests
// can assert on which props it receives without rendering its full tree.
vi.mock('./CreateFixRequestPanel.jsx', () => ({
  default: ({ onClose, onSubmitted }) => (
    <div role="dialog" aria-label="Submit fix report">
      <button type="button" aria-label="Close" onClick={onClose}>
        <span className="material-symbols-outlined">close</span>
      </button>
      <button type="button" data-testid="stub-fix-submitted" onClick={() => onSubmitted?.()}>
        Stub submitted
      </button>
    </div>
  ),
}))

vi.mock('./CreateReportPanel.jsx', () => ({
  default: ({ editReport, onClose, onUpdated, onError }) => (
    <div data-testid="create-report-panel">
      <span data-testid="cp-report-id">{editReport?.id}</span>
      <button onClick={onClose}>Cancel</button>
      {onUpdated && (
        <button
          type="button"
          data-testid="stub-edit-save"
          onClick={() => onUpdated({ ...editReport, description: 'Updated via stub' })}
        >
          Stub save
        </button>
      )}
      {onError && (
        <button type="button" data-testid="stub-edit-error" onClick={() => onError('Save failed')}>
          Stub error
        </button>
      )}
    </div>
  ),
}))

// ContributeMeasurementsPanel stub exposes onClose and onContributed so tests
// can trigger them without running the real PATCH flow.
vi.mock('./ContributeMeasurementsPanel.jsx', () => ({
  default: ({ report, object, onClose, onContributed }) => (
    <div data-testid="contribute-panel">
      <span data-testid="contribute-object-type">{object?.objectType}</span>
      <button onClick={onClose}>Close contribute</button>
      <button onClick={() => onContributed({ id: report?.id })}>Submit contribute</button>
    </div>
  ),
}))

vi.mock('../services/reportService.js', () => ({
  agreeReport: vi.fn(),
  disagreeReport: vi.fn(),
  updateReport: vi.fn(),
  deleteReport: vi.fn(() => Promise.resolve()),
  submitFixRequest: vi.fn(),
  agreeFixRequest: vi.fn(),
  disagreeFixRequest: vi.fn(),
  contributeMeasurements: vi.fn(),
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
  deleteReport,
  agreeFixRequest,
  disagreeFixRequest,
  getCommentsByReport,
  createComment,
  followReport,
  unfollowReport,
  getFollowStatus,
} from '../services/reportService.js'
import { useUserProfile } from '../hooks/useUserProfile.js'

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
    getFollowStatus.mockResolvedValue({ following: false })
    useUserProfile.mockReturnValue({ data: undefined })
    // Reset auth defaults between tests so admin/owner flags don't leak.
    mockAuth.token = 'mock-token'
    mockAuth.isAuthenticated = true
    mockAuth.userId = 'user123'
    mockAuth.isAdmin = false
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

  test('shows Back to feed and navigates to /feed when clicked', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/?from=feed']}>
          <Routes>
            <Route
              path="/"
              element={
                <ReportPanel
                  report={report}
                  onClose={onCloseMock}
                  onVoteChange={onVoteChangeMock}
                  onVoteUpdate={onVoteUpdateMock}
                  onFollowChange={onFollowChangeMock}
                />
              }
            />
            <Route path="/feed" element={<div data-testid="feed-route">feed</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('button', { name: /back to feed/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /back to feed/i }))
    await waitFor(() => expect(screen.getByTestId('feed-route')).toBeInTheDocument())
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

  test('voting invalidates leaderboard and currentUser caches', async () => {
    // A successful vote earns +5 / removes -5 points. The caller's tab must
    // pick that up on the next leaderboard/profile visit without waiting
    // for the public SSE event to arrive.
    agreeReport.mockResolvedValueOnce({ id: 'r1', agrees: 1, disagrees: 0, userVote: 'agree' })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReportPanel
            report={report}
            onClose={onCloseMock}
            onVoteChange={onVoteChangeMock}
            onVoteUpdate={onVoteUpdateMock}
            onFollowChange={onFollowChangeMock}
          />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await act(async () => { await user.click(screen.getByLabelText('Agree')) })

    await waitFor(() => {
      expect(spy).toHaveBeenCalledWith({ queryKey: ['leaderboard'] })
      expect(spy).toHaveBeenCalledWith({ queryKey: ['currentUser'] })
    })
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

  describe('edit button visibility', () => {
    const ownedReport = { ...report, ownerId: 'user123', environment: 'OUTDOOR' }
    const othersReport = { ...report, ownerId: 'someone-else', environment: 'OUTDOOR' }

    test('owner sees "Edit" button (full edit access)', () => {
      renderPanel({ report: ownedReport })
      expect(screen.getByRole('button', { name: /^edit$/i })).toBeInTheDocument()
    })

    test('authenticated non-owner sees no edit button in the header', () => {
      renderPanel({ report: othersReport })
      expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /edit measurements/i })).not.toBeInTheDocument()
    })

    test('unauthenticated user sees no edit button at all', () => {
      mockAuth.isAuthenticated = false
      mockAuth.token = null
      renderPanel({ report: othersReport })
      expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument()
    })

    test('clicking Edit opens CreateReportPanel for owner', async () => {
      renderPanel({ report: ownedReport })
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      expect(screen.getByTestId('create-report-panel')).toBeInTheDocument()
      expect(screen.getByTestId('cp-report-id')).toHaveTextContent('r1')
    })

    test('closing the edit panel hides CreateReportPanel', async () => {
      renderPanel({ report: ownedReport })
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      expect(screen.getByTestId('create-report-panel')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /^cancel$/i }))
      expect(screen.queryByTestId('create-report-panel')).not.toBeInTheDocument()
    })
  })

  // ── "Add a missing measurement" per-object button ────────────────────────

  describe('"Add a missing measurement" contribute button', () => {
    // RAMP with no measurements → all 3 fields blank
    const reportWithBlankMeasurements = {
      ...report,
      ownerId: 'someone-else',
      objects: [{ id: 1, objectType: 'RAMP', issues: ['TOO_STEEP'], measurements: {} }],
    }
    // RAMP with all 3 fields filled → no blank fields
    const reportWithFullMeasurements = {
      ...report,
      ownerId: 'someone-else',
      objects: [{ id: 1, objectType: 'RAMP', issues: [], measurements: { slope_percent: 8, width_cm: 120, height_cm: 50 } }],
    }
    const ownedReportWithObjects = {
      ...report,
      ownerId: 'user123',
      objects: [{ id: 1, objectType: 'RAMP', issues: [], measurements: {} }],
    }

    test('non-owner with blank measurement fields sees the button', () => {
      renderPanel({ report: reportWithBlankMeasurements })
      expect(screen.getByRole('button', { name: /add a missing measurement/i })).toBeInTheDocument()
    })

    test('non-owner with all measurements filled does not see the button', () => {
      renderPanel({ report: reportWithFullMeasurements })
      expect(screen.queryByRole('button', { name: /add a missing measurement/i })).not.toBeInTheDocument()
    })

    test('owner does not see the per-object contribute button', () => {
      renderPanel({ report: ownedReportWithObjects })
      expect(screen.queryByRole('button', { name: /add a missing measurement/i })).not.toBeInTheDocument()
    })

    test('unauthenticated user does not see the contribute button', () => {
      mockAuth.isAuthenticated = false
      mockAuth.token = null
      renderPanel({ report: reportWithBlankMeasurements })
      expect(screen.queryByRole('button', { name: /add a missing measurement/i })).not.toBeInTheDocument()
    })

    test('clicking the button opens ContributeMeasurementsPanel for that object', async () => {
      renderPanel({ report: reportWithBlankMeasurements })
      await user.click(screen.getByRole('button', { name: /add a missing measurement/i }))
      expect(screen.getByTestId('contribute-panel')).toBeInTheDocument()
      expect(screen.getByTestId('contribute-object-type')).toHaveTextContent('RAMP')
    })

    test('closing ContributeMeasurementsPanel via onClose hides the panel', async () => {
      renderPanel({ report: reportWithBlankMeasurements })
      await user.click(screen.getByRole('button', { name: /add a missing measurement/i }))
      expect(screen.getByTestId('contribute-panel')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /close contribute/i }))
      expect(screen.queryByTestId('contribute-panel')).not.toBeInTheDocument()
    })

    test('onContributed calls onVoteUpdate and shows success toast', async () => {
      renderPanel({ report: reportWithBlankMeasurements })
      await user.click(screen.getByRole('button', { name: /add a missing measurement/i }))

      await user.click(screen.getByRole('button', { name: /submit contribute/i }))

      await waitFor(() => expect(onVoteUpdateMock).toHaveBeenCalled())
      expect(screen.getByText(/measurements added/i)).toBeInTheDocument()
    })
  })

  describe('owner / admin delete', () => {
    const ownedReport = { ...report, ownerId: 'user123', environment: 'OUTDOOR' }
    const otherReport = { ...report, ownerId: 'someone-else', environment: 'OUTDOOR' }

    test('does not show Delete button for non-owner non-admin', () => {
      renderPanel({ report: otherReport })
      expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
    })

    test('shows Delete button for the report owner', () => {
      renderPanel({ report: ownedReport })
      expect(screen.getByRole('button', { name: /delete report|^delete$/i })).toBeInTheDocument()
    })

    test('shows Delete button for an admin even when not the owner', () => {
      mockAuth.isAdmin = true
      renderPanel({ report: otherReport })
      expect(screen.getByRole('button', { name: /delete report|^delete$/i })).toBeInTheDocument()
    })

    test('clicking Delete + confirming calls deleteReport and closes the panel', async () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      deleteReport.mockResolvedValueOnce(undefined)
      renderPanel({ report: ownedReport })

      await user.click(screen.getByRole('button', { name: /delete report|^delete$/i }))

      await waitFor(() => {
        expect(deleteReport).toHaveBeenCalledWith('r1', 'mock-token')
      })
      expect(onCloseMock).toHaveBeenCalled()
      confirmSpy.mockRestore()
    })

    test('clicking Delete and dismissing the confirm does not call deleteReport', async () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
      renderPanel({ report: ownedReport })

      await user.click(screen.getByRole('button', { name: /delete report|^delete$/i }))

      expect(deleteReport).not.toHaveBeenCalled()
      expect(onCloseMock).not.toHaveBeenCalled()
      confirmSpy.mockRestore()
    })

    test('shows a permission toast on 403 and does not close the panel', async () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      deleteReport.mockRejectedValueOnce(new Error('Request failed: 403'))
      renderPanel({ report: ownedReport })

      await user.click(screen.getByRole('button', { name: /delete report|^delete$/i }))

      await screen.findByText(/permission/i)
      expect(onCloseMock).not.toHaveBeenCalled()
      confirmSpy.mockRestore()
    })

    test('shows a "no longer exists" toast on 404', async () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
      deleteReport.mockRejectedValueOnce(new Error('Request failed: 404'))
      renderPanel({ report: ownedReport })

      await user.click(screen.getByRole('button', { name: /delete report|^delete$/i }))

      await screen.findByText(/no longer exists/i)
      confirmSpy.mockRestore()
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

  test('opens the fix-request sheet when the fix CTA row is activated', async () => {
    renderPanel()
    await user.click(screen.getByRole('button', { name: /has this been fixed/i }))
    expect(await screen.findByRole('dialog', { name: /submit fix report/i })).toBeInTheDocument()
  })

  test('closes the fix-request sheet without submitting', async () => {
    renderPanel()
    await user.click(screen.getByRole('button', { name: /has this been fixed/i }))
    const dlg = await screen.findByRole('dialog', { name: /submit fix report/i })
    await user.click(within(dlg).getByRole('button', { name: /^close$/i }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /submit fix report/i })).not.toBeInTheDocument(),
    )
  })

  test('fires fix submission side-effects when the fix sheet completes', async () => {
    const invalidateSpy = vi.spyOn(QueryClient.prototype, 'invalidateQueries')
    renderPanel()
    await user.click(screen.getByRole('button', { name: /has this been fixed/i }))
    await user.click(screen.getByTestId('stub-fix-submitted'))
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalled())
    invalidateSpy.mockRestore()
  })

  test('shows a spinner on the follow control while the follow request is pending', async () => {
    followReport.mockImplementationOnce(() => new Promise(() => {}))
    renderPanel()
    const followBtn = screen.getByRole('button', { name: /follow updates/i })
    await act(async () => {
      await user.click(followBtn)
    })
    expect(followBtn.querySelector('.animate-spin')).toBeTruthy()
  })

  test('handles unfollow failures without crashing', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    getFollowStatus.mockResolvedValueOnce({ following: true })
    unfollowReport.mockRejectedValueOnce(new Error('network'))
    renderPanel()
    await waitFor(() => expect(screen.getByRole('button', { name: /unfollow/i })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /unfollow/i }))
    await waitFor(() => expect(errSpy).toHaveBeenCalled())
    errSpy.mockRestore()
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

  test('clicking No, still there calls disagreeFixRequest', async () => {
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
    disagreeFixRequest.mockResolvedValueOnce({ ...activeFix, disagrees: 1, userVote: 'disagree' })
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /no, still there/i }))
    })

    await waitFor(() => {
      expect(disagreeFixRequest).toHaveBeenCalledWith('r1', 11, 'mock-token')
    })
  })

  test('posts a new comment and prepends it to the thread', async () => {
    createComment.mockResolvedValueOnce({
      id: 'n1',
      content: 'Thanks for the detailed report.',
      author: { id: 'user123', name: 'Tester' },
      createdAt: '2026-05-02T12:00:00Z',
    })
    renderPanel()
    await user.type(screen.getByPlaceholderText(/add a comment/i), 'Thanks for the detailed report.')
    await act(async () => {
      await user.click(screen.getByRole('button', { name: /^post$/i }))
    })
    await waitFor(() => {
      expect(createComment).toHaveBeenCalledWith(
        'r1',
        'Thanks for the detailed report.',
        'mock-token',
        'user123',
      )
    })
    expect(screen.getByText('Thanks for the detailed report.')).toBeInTheDocument()
  })

  describe('reporter row', () => {
    const reportWithOwner = {
      ...report,
      ownerId: 42,
      reportedBy: 'User #42',
      date: '9 May 2026',
    }

    it('shows the real username from useUserProfile instead of the "User #N" fallback', () => {
      useUserProfile.mockReturnValue({ data: { name: 'Alice Mapper', avatarUrl: null } })
      renderPanel({ report: reportWithOwner })
      expect(screen.getByText('Alice Mapper')).toBeInTheDocument()
      expect(screen.queryByText('User #42')).not.toBeInTheDocument()
    })

    it('falls back to "User #N" while the profile is still loading', () => {
      useUserProfile.mockReturnValue({ data: undefined })
      renderPanel({ report: reportWithOwner })
      expect(screen.getByText('User #42')).toBeInTheDocument()
    })

    it('renders the author top badge chip next to the username when present', () => {
      useUserProfile.mockReturnValue({ data: { name: 'Alice Mapper', avatarUrl: null } })
      renderPanel({ report: { ...reportWithOwner, authorTopBadge: 'TOP_10' } })
      // BadgeIcon (size="sm") exposes the badge label via aria-label.
      expect(screen.getByLabelText('Top 10')).toBeInTheDocument()
    })

    it('omits the author badge when the report has no top badge', () => {
      useUserProfile.mockReturnValue({ data: { name: 'Alice Mapper', avatarUrl: null } })
      renderPanel({ report: { ...reportWithOwner, authorTopBadge: null } })
      expect(screen.queryByLabelText('Top 10')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Trusted Reporter')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Expert Mapper')).not.toBeInTheDocument()
    })

    it('navigates to /profile/:ownerId when the reporter row is clicked', async () => {
      useUserProfile.mockReturnValue({ data: { name: 'Alice Mapper', avatarUrl: null } })
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route path="/" element={
                <>
                  <ReportPanel
                    report={reportWithOwner}
                    onClose={onCloseMock}
                    onVoteChange={onVoteChangeMock}
                    onVoteUpdate={onVoteUpdateMock}
                    onFollowChange={onFollowChangeMock}
                  />
                  <LocationProbe />
                </>
              } />
              <Route path="/profile/:userId" element={<LocationProbe />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      )
      await user.click(screen.getByText('Alice Mapper'))
      await waitFor(() => {
        expect(screen.getByTestId('location-probe')).toHaveTextContent('/profile/42')
      })
    })
  })

  describe('media rendering', () => {
    test('renders an <img> when the report image URL is a JPEG', () => {
      const { container } = renderPanel({
        report: { ...report, image: 'https://cdn.example.com/foo.jpg' },
      })
      expect(container.querySelector('img[src="https://cdn.example.com/foo.jpg"]')).toBeTruthy()
      expect(container.querySelector('video')).toBeFalsy()
    })

    test('renders a <video> when the report image URL is an MP4', () => {
      const { container } = renderPanel({
        report: { ...report, image: 'https://cdn.example.com/clip.mp4' },
      })
      expect(container.querySelector('video[src="https://cdn.example.com/clip.mp4"]')).toBeTruthy()
    })

    test('renders a <video> when the report image URL is a MOV (Apple default)', () => {
      const { container } = renderPanel({
        report: { ...report, image: 'https://cdn.example.com/clip.MOV?X-Amz-Sig=abc' },
      })
      expect(container.querySelector('video')).toBeTruthy()
    })

    test('renders prev/next/dot controls when the report has multiple images via mediaUrls', () => {
      renderPanel({
        report: {
          ...report,
          mediaUrls: [
            'https://cdn.example.com/a.jpg',
            'https://cdn.example.com/b.jpg',
            'https://cdn.example.com/c.jpg',
          ],
        },
      })
      expect(screen.getByLabelText('Previous')).toBeInTheDocument()
      expect(screen.getByLabelText('Next')).toBeInTheDocument()
      expect(screen.getByLabelText('Go to media 1')).toBeInTheDocument()
      expect(screen.getByLabelText('Go to media 3')).toBeInTheDocument()
    })

    test('renders prev/next/dot controls when the report has multiple media via report.media (post-upload shape)', () => {
      renderPanel({
        report: {
          ...report,
          media: [
            { id: 1, url: 'https://cdn.example.com/a.jpg' },
            { id: 2, url: 'https://cdn.example.com/b.jpg' },
          ],
        },
      })
      expect(screen.getByLabelText('Previous')).toBeInTheDocument()
      expect(screen.getByLabelText('Next')).toBeInTheDocument()
      expect(screen.getByLabelText('Go to media 1')).toBeInTheDocument()
      expect(screen.getByLabelText('Go to media 2')).toBeInTheDocument()
    })

    test('embeds JSON-LD when the report has coordinates and objects', () => {
      renderPanel({
        report: {
          ...report,
          latitude: 41.06,
          longitude: 29.04,
          objects: [{ objectType: 'RAMP', issues: ['TOO_STEEP'] }],
        },
      })
      const el = screen.getByTestId('report-jsonld')
      expect(el).toBeInTheDocument()
      expect(el.textContent).toContain('schema.org')
    })

    test('opens the media lightbox from a photo slide and closes it with Escape', async () => {
      renderPanel({
        report: {
          ...report,
          mediaUrls: ['https://cdn.example.com/a.jpg', 'https://cdn.example.com/b.jpg'],
        },
      })
      await user.click(
        screen.getByRole('button', { name: /open broken elevator photo 1 in viewer/i }),
      )
      expect(screen.getByRole('dialog', { name: /media viewer/i })).toBeInTheDocument()
      await user.keyboard('{Escape}')
      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /media viewer/i })).not.toBeInTheDocument(),
      )
    })

    test('opens the lightbox from a video slide via the expand control', async () => {
      renderPanel({
        report: { ...report, mediaUrls: ['https://cdn.example.com/clip.mp4'] },
      })
      await user.click(screen.getByRole('button', { name: /open in viewer/i }))
      expect(screen.getByRole('dialog', { name: /media viewer/i })).toBeInTheDocument()
    })

    test('advances slides in the fullscreen viewer with arrow keys', async () => {
      renderPanel({
        report: {
          ...report,
          mediaUrls: ['https://cdn.example.com/a.jpg', 'https://cdn.example.com/b.jpg'],
        },
      })
      await user.click(
        screen.getByRole('button', { name: /open broken elevator photo 1 in viewer/i }),
      )
      expect(screen.getByText('1 / 2')).toBeInTheDocument()
      await user.keyboard('{ArrowRight}')
      expect(screen.getByText('2 / 2')).toBeInTheDocument()
    })
  })

  test('shows an error when voting fails', async () => {
    agreeReport.mockRejectedValueOnce(new Error('network'))
    renderPanel()
    await user.click(screen.getByLabelText('Agree'))
    expect(await screen.findByText(/failed to submit vote/i)).toBeInTheDocument()
  })

  test('shows an error when voting on the fix request fails', async () => {
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
    disagreeFixRequest.mockRejectedValueOnce(new Error('network'))
    renderPanel({ report: { ...report, activeFixRequest: activeFix } })
    await user.click(screen.getByRole('button', { name: /no, still there/i }))
    expect(await screen.findByText(/failed to vote on fix/i)).toBeInTheDocument()
  })

  test('routes toast messages through onShowToast when the prop is provided', async () => {
    const onShowToast = vi.fn()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    deleteReport.mockRejectedValueOnce(new Error('Request failed: 403'))
    renderPanel({
      report: { ...report, ownerId: 'user123', environment: 'OUTDOOR' },
      onShowToast,
    })
    await user.click(screen.getByRole('button', { name: /delete report|^delete$/i }))
    await waitFor(() =>
      expect(onShowToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'error' })),
    )
    confirmSpy.mockRestore()
  })

  test('notifies onVoteUpdate after a successful edit save', async () => {
    const ownedReport = { ...report, ownerId: 'user123', environment: 'OUTDOOR' }
    renderPanel({ report: ownedReport })
    await user.click(screen.getByRole('button', { name: /^edit$/i }))
    await user.click(screen.getByTestId('stub-edit-save'))
    await waitFor(() => expect(onVoteUpdateMock).toHaveBeenCalled())
  })

  test('shows an error toast when edit mode reports a failure', async () => {
    const ownedReport = { ...report, ownerId: 'user123', environment: 'OUTDOOR' }
    renderPanel({ report: ownedReport })
    await user.click(screen.getByRole('button', { name: /^edit$/i }))
    await user.click(screen.getByTestId('stub-edit-error'))
    expect(await screen.findByText('Save failed')).toBeInTheDocument()
  })

  test('renders the mobile sheet resize slider when the viewport is narrow', () => {
    const prev = window.matchMedia
    window.matchMedia = vi.fn().mockImplementation((query) => ({
      matches: String(query).includes('1023'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
    try {
      renderPanel()
      expect(screen.getByRole('slider', { name: /resize report panel/i })).toBeInTheDocument()
    } finally {
      window.matchMedia = prev
    }
  })
})