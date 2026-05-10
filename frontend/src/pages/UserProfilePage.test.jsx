import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import UserProfilePage from './UserProfilePage.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../hooks/useUserProfile.js', () => ({
  useUserProfile: vi.fn(),
}))
vi.mock('../hooks/useUserReports.js', () => ({
  useUserReports: vi.fn(),
  useUpdateReport: vi.fn(),
  useDeleteReport: vi.fn(),
}))
vi.mock('../components/Navbar.jsx', () => ({
  default: () => <div data-testid="navbar" />,
}))

import { useAuth } from '../context/AuthContext.jsx'
import { useUserProfile } from '../hooks/useUserProfile.js'
import { useUserReports, useUpdateReport, useDeleteReport } from '../hooks/useUserReports.js'

const SAMPLE_USER = {
  id: 42,
  name: 'Bob Builder',
  bio: 'Accessibility mapper.',
  avatarUrl: null,
  role: 'USER',
  contributionStats: { reportsSubmitted: 5, routesPlanned: 2 },
  points: 320,
  rank: 4,
  badges: ['TRUSTED_REPORTER', 'TOP_10'],
  topBadge: 'TOP_10',
  leaderboardHidden: false,
}

function renderPage(userId = '42') {
  return render(
    <MemoryRouter initialEntries={[`/profile/${userId}`]}>
      <Routes>
        <Route path="/profile/:userId" element={<UserProfilePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('UserProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ token: 'tok', userId: 1, isAuthenticated: true })
    useUserProfile.mockReturnValue({ data: SAMPLE_USER, isPending: false, isError: false })
    useUserReports.mockReturnValue({ data: [], isPending: false, isError: false })
    useUpdateReport.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    useDeleteReport.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  })

  it('shows a loading state while the profile is pending', () => {
    useUserProfile.mockReturnValue({ data: undefined, isPending: true, isError: false })
    renderPage()
    expect(screen.getByText(/loading profile/i)).toBeInTheDocument()
  })

  it('shows an error state when the user is not found', () => {
    useUserProfile.mockReturnValue({ data: undefined, isPending: false, isError: true })
    renderPage()
    expect(screen.getByText(/user not found/i)).toBeInTheDocument()
  })

  it('renders name, role, bio, and contribution stats — without any edit controls', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Bob Builder' })).toBeInTheDocument()
    expect(screen.getByText('USER')).toBeInTheDocument()
    expect(screen.getByText('Accessibility mapper.')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument()
  })

  it('does not show the viewed user\'s email', () => {
    renderPage()
    expect(screen.queryByText(/@/)).not.toBeInTheDocument()
  })

  it('shows "No reports submitted yet." (not the owner CTA) when the user has no reports', () => {
    renderPage()
    expect(screen.getByText(/no reports submitted yet/i)).toBeInTheDocument()
    expect(screen.queryByText(/add one on the map/i)).not.toBeInTheDocument()
  })

  it('uses "Reports" as the section heading, not "My Reports"', () => {
    useUserReports.mockReturnValue({ data: [], isPending: false, isError: false })
    renderPage()
    expect(screen.getByText(/^reports \(0\)$/i)).toBeInTheDocument()
    expect(screen.queryByText(/my reports/i)).not.toBeInTheDocument()
  })

  describe('gamification', () => {
    it('renders points, rank, and badges for a public profile', () => {
      renderPage()
      expect(screen.getByText('320')).toBeInTheDocument()
      expect(screen.getByText('#4')).toBeInTheDocument()
      // Both badges show their human-readable labels.
      expect(screen.getByText('Trusted Reporter')).toBeInTheDocument()
      expect(screen.getByText('Top 10')).toBeInTheDocument()
    })

    it('omits the rank tile entirely when the viewed user has opted out', () => {
      // A foreign profile must not leak even the existence of a rank for an
      // opt-out user — the owner of the profile sees their own rank as
      // "Hidden" but other viewers see no tile at all.
      useUserProfile.mockReturnValue({
        data: { ...SAMPLE_USER, rank: null, leaderboardHidden: true },
        isPending: false,
        isError: false,
      })
      renderPage()
      expect(screen.queryByText('Rank')).not.toBeInTheDocument()
      expect(screen.queryByText('Hidden')).not.toBeInTheDocument()
    })

    it('omits the badges section when the viewed user has none', () => {
      useUserProfile.mockReturnValue({
        data: { ...SAMPLE_USER, badges: [], topBadge: null },
        isPending: false,
        isError: false,
      })
      renderPage()
      expect(screen.queryByRole('heading', { name: /^badges$/i })).not.toBeInTheDocument()
    })

    it('does not render an opt-out toggle on a foreign profile', () => {
      // Visibility toggle is the owner's privilege — never visible elsewhere.
      renderPage()
      expect(screen.queryByRole('checkbox', { name: /hide me/i })).not.toBeInTheDocument()
    })
  })
})
