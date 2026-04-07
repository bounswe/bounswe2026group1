import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReportPanel from './ReportPanel.jsx'
import * as reportService from '../services/reportService.js'

// 1. Mock Auth Context
vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => ({ 
    token: 'mock-token', 
    isAuthenticated: true, 
    userId: 'user123' 
  }),
}))

// 2. Mock the Service
vi.mock('../services/reportService.js', () => ({
  agreeReport: vi.fn(),
  disagreeReport: vi.fn(),
  mapReport: vi.fn(r => r),
  getCommentsByReport: vi.fn(() => Promise.resolve([])),
  createComment: vi.fn(),
  deleteComment: vi.fn(() => Promise.resolve()),
}))

describe('ReportPanel', () => {
  let onCloseMock, onVoteChangeMock, onFollowChangeMock, onVoteUpdateMock, user, queryClient

  const MOCK_REPORT = {
    id: 'r1',
    title: 'Broken Elevator',
    description: 'The elevator is out of service.',
    location: '41.0683, 29.0505',
    reportedBy: 'Tester',
    date: '07 Apr 2026',
    status: 'unverified',
    agrees: 0,
    disagrees: 0,
    imageUrl: null,
    userVote: null,
    isFollowed: false,
    tags: []
  }

  const existingComment = {
    id: 'c1',
    content: 'Existing comment',
    author: { id: 'user123', name: 'Tester' },
    createdAt: '2026-04-07T10:00:00Z'
  }

  beforeEach(() => {
    onCloseMock = vi.fn()
    onVoteChangeMock = vi.fn()
    onFollowChangeMock = vi.fn()
    onVoteUpdateMock = vi.fn()
    user = userEvent.setup()
    vi.clearAllMocks()
    reportService.getCommentsByReport.mockResolvedValue([])

    // Fresh QueryClient for each test avoids cache pollution
    queryClient = new QueryClient({ 
      defaultOptions: { 
        queries: { retry: false }, 
        mutations: { retry: false } 
      } 
    })
  })

  afterEach(() => {
    queryClient.clear()
  })

  // Async render function that waits for initial data fetches to resolve
  async function renderPanel(props = {}) {
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReportPanel
            report={MOCK_REPORT}
            onClose={onCloseMock}
            onVoteChange={onVoteChangeMock}
            onVoteUpdate={onVoteUpdateMock}
            onFollowChange={onFollowChangeMock}
            {...props}
          />
        </MemoryRouter>
      </QueryClientProvider>
    )
    
    // This eliminates the act() warnings by waiting for the initial mount fetches!
    await waitFor(() => {
      expect(reportService.getCommentsByReport).toHaveBeenCalled()
    })
  }

  test('renders basic report information', async () => {
    await renderPanel()
    expect(screen.getByText(MOCK_REPORT.title)).toBeInTheDocument()
    expect(screen.getByText(MOCK_REPORT.description)).toBeInTheDocument()
    expect(screen.getByText(/41\.0683/)).toBeInTheDocument()
  })

  test('calls onVoteChange with agree when Agree is clicked', async () => {
    reportService.agreeReport.mockResolvedValue({ ...MOCK_REPORT, agrees: 1, userVote: 'AGREE' })
    reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, agrees: 1, userVote: 'agree' })

    await renderPanel()
    const agreeBtn = screen.getByLabelText('Agree')

    await user.click(agreeBtn)
    
    await waitFor(() => {
      expect(onVoteChangeMock).toHaveBeenCalledWith('agree')
    })
  })

  test('calls onVoteChange with disagree when Disagree is clicked', async () => {
    reportService.disagreeReport.mockResolvedValue({ ...MOCK_REPORT, disagrees: 1, userVote: 'DISAGREE' })
    reportService.mapReport.mockReturnValue({ ...MOCK_REPORT, disagrees: 1, userVote: 'disagree' })

    await renderPanel()
    const disagreeBtn = screen.getByLabelText('Disagree')

    await user.click(disagreeBtn)
    
    await waitFor(() => {
      expect(onVoteChangeMock).toHaveBeenCalledWith('disagree')
    })
  })

  test('toggles follow state correctly', async () => {
    await renderPanel()
    const followBtn = screen.getByRole('button', { name: /follow/i })
    
    await user.click(followBtn)

    await waitFor(() => {
      expect(onFollowChangeMock).toHaveBeenCalled()
    })
  })

  test('submits a new comment', async () => {
    reportService.createComment.mockResolvedValueOnce({
      id: 'c2',
      content: 'New test comment',
      author: { id: 'user123', name: 'Tester' },
      createdAt: new Date().toISOString()
    })

    await renderPanel()
    const textarea = screen.getByPlaceholderText(/Add a comment/i)
    const postBtn = screen.getByText('Post')

    await user.type(textarea, 'New test comment')
    await user.click(postBtn)

    await waitFor(() => {
      expect(screen.getByText('New test comment')).toBeInTheDocument()
    })
  })

  test('can delete own comment', async () => {
    reportService.getCommentsByReport.mockResolvedValueOnce([existingComment])

    await renderPanel()
    
    const commentText = await screen.findByText((content, element) =>
      element.tagName.toLowerCase() === 'p' && content.includes('Existing comment')
    )
    const deleteBtn = screen.getByLabelText('Delete comment')

    await user.click(deleteBtn)

    await waitFor(() => {
      expect(reportService.deleteComment).toHaveBeenCalledWith(existingComment.id, expect.anything())
      expect(commentText).not.toBeInTheDocument()
    })
  })
})