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

vi.mock('../services/reportService.js', () => ({
  agreeReport: vi.fn(),
  disagreeReport: vi.fn(),
  updateReport: vi.fn(),
  deleteReport: vi.fn(() => Promise.resolve()),
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
  updateReport,
  deleteReport,
  agreeFixRequest,
  getCommentsByReport,
  createComment,
  deleteComment,
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

  describe('owner edit', () => {
    const ownedReport = {
      ...report,
      ownerId: 'user123',
      environment: 'OUTDOOR',
    }

    test('does not show Edit button for non-owners', () => {
      renderPanel({ report: { ...ownedReport, ownerId: 'someone-else' } })
      expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    })

    test('shows Edit button for the report owner', () => {
      renderPanel({ report: ownedReport })
      expect(screen.getByRole('button', { name: /^edit$/i })).toBeInTheDocument()
    })

    test('clicking Edit reveals description textarea and environment radios', async () => {
      renderPanel({ report: ownedReport })
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      expect(screen.getByLabelText(/^description$/i)).toHaveValue(ownedReport.description)
      expect(screen.getByRole('radio', { name: /outdoor/i })).toBeChecked()
      expect(screen.getByRole('radio', { name: /indoor/i })).not.toBeChecked()
    })

    test('save calls updateReport with description and environment', async () => {
      updateReport.mockResolvedValueOnce({ ...ownedReport, description: 'New' })
      renderPanel({ report: ownedReport })
      await user.click(screen.getByRole('button', { name: /^edit$/i }))

      const textarea = screen.getByLabelText(/^description$/i)
      await user.clear(textarea)
      await user.type(textarea, 'New')
      await user.click(screen.getByRole('radio', { name: /indoor/i }))
      await user.click(screen.getByRole('button', { name: /^save$/i }))

      await waitFor(() => {
        expect(updateReport).toHaveBeenCalledWith(
          'r1',
          { description: 'New', environment: 'INDOOR' },
          'mock-token',
        )
      })
    })

    test('Cancel exits edit mode without saving', async () => {
      renderPanel({ report: ownedReport })
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      await user.click(screen.getByRole('button', { name: /^cancel$/i }))
      expect(screen.queryByLabelText(/^description$/i)).not.toBeInTheDocument()
      expect(updateReport).not.toHaveBeenCalled()
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
})