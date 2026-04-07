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
  mapReport: vi.fn(r => r),
  getCommentsByReport: vi.fn(() => Promise.resolve([])),
  createComment: vi.fn(),
  deleteComment: vi.fn(() => Promise.resolve()),
}))

import {
  agreeReport,
  disagreeReport,
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
      .mockResolvedValueOnce({ id: 'r1', agrees: 1, disagrees: 0 })
      .mockResolvedValueOnce({ id: 'r1', agrees: 0, disagrees: 0 })
    disagreeReport.mockResolvedValueOnce({ id: 'r1', agrees: 0, disagrees: 1 })

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

    it('calls onVoteChange with agree when agree vote is cast', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 4, userVote: 'AGREE' })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 4, userVote: 'agree' })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith('agree')
      })
    })

    it('calls onVoteChange with disagree when disagree vote is cast', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      const onVoteChange = vi.fn()
      reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 2, userVote: 'DISAGREE' })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, disagrees: 2, userVote: 'disagree' })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, onVoteChange })


    const textarea = screen.getByPlaceholderText('Add a comment...')
    const postBtn = screen.getByText(/post/i)

    await user.type(textarea, 'New comment')
    await act(async () => { await user.click(postBtn) })


    it('calls onVoteChange with null when toggling off an existing agree vote', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      const onVoteChange = vi.fn()
      reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 2, userVote: null })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 2, userVote: null })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, userVote: 'agree', onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Agree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith(null)
      })

    })

    it('calls onVoteChange with null when toggling off an existing disagree vote', async () => {
      const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'
      const onVoteChange = vi.fn()
      reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 0, userVote: null })
      reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, disagrees: 0, userVote: null })
      const user = userEvent.setup()
      renderPanel({ token: fakeToken, userVote: 'disagree', onVoteChange })

      await user.click(screen.getByRole('button', { name: 'Disagree' }))

      await waitFor(() => {
        expect(onVoteChange).toHaveBeenCalledWith(null)
      })
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
