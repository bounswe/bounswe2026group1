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
  updateReport: vi.fn(),
  mapReport: vi.fn(r => r),
  getCommentsByReport: vi.fn(() => Promise.resolve([])),
  createComment: vi.fn(),
  deleteComment: vi.fn(() => Promise.resolve()),
}))

import {
  agreeReport,
  disagreeReport,
  updateReport,
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
})