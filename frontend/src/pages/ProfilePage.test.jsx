import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import ProfilePage from './ProfilePage.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../hooks/useCurrentUser.js', () => ({
  useCurrentUser: vi.fn(),
  currentUserKey: ['currentUser'],
}))
vi.mock('../hooks/useProfile.js', () => ({
  useUpdateProfile: vi.fn(),
  useUploadAvatar: vi.fn(),
  useDeleteAvatar: vi.fn(),
}))
vi.mock('../hooks/useGamification.js', () => ({
  useSetLeaderboardVisibility: vi.fn(),
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
import { useCurrentUser } from '../hooks/useCurrentUser.js'
import { useDeleteAvatar, useUpdateProfile, useUploadAvatar } from '../hooks/useProfile.js'
import { useSetLeaderboardVisibility } from '../hooks/useGamification.js'
import { useDeleteReport, useUpdateReport, useUserReports } from '../hooks/useUserReports.js'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location-probe">{location.pathname + location.search}</div>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/profile']}>
      <Routes>
        <Route path="/profile" element={<><ProfilePage /><LocationProbe /></>} />
        <Route path="/" element={<><div>map page</div><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  )
}

const SAMPLE_USER = {
  id: 7,
  name: 'Ada Lovelace',
  email: 'ada@example.com',
  bio: 'Mathematician.',
  avatarUrl: null,
  role: 'USER',
  contributionStats: { reportsSubmitted: 3, routesPlanned: 1 },
  points: 245,
  rank: 12,
  badges: ['TRUSTED_REPORTER'],
  topBadge: 'TRUSTED_REPORTER',
  leaderboardHidden: false,
}

// Shape produced by useUserReports → mapReport (see services/reportService.js).
const SAMPLE_REPORT = {
  id: 100,
  title: 'Sidewalk Issue',
  description: 'Broken sidewalk near campus.',
  status: 'verified',
  date: '1 April 2026',
  fixedAt: null,
  location: '41.0000, 29.0000',
  reportedBy: 'User #7',
  ownerId: 7,
  environment: 'OUTDOOR',
  agrees: 6,
  disagrees: 1,
  userVote: null,
  reportType: 'OBSTACLE',
  objects: [{ objectType: 'SIDEWALK', issues: [], measurements: {} }],
  image: null,
  mediaUrls: [],
  latitude: 41.0,
  longitude: 29.0,
  activeFixRequest: null,
}

describe('ProfilePage', () => {
  const updateProfileMutate = vi.fn()
  const uploadAvatarMutate = vi.fn()
  const deleteAvatarMutate = vi.fn()
  const setVisibilityMutate = vi.fn()
  const updateReportMutate = vi.fn()
  const deleteReportMutate = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ token: 'tok', userId: 7, isAuthenticated: true })
    useCurrentUser.mockReturnValue({ data: SAMPLE_USER, isPending: false, isError: false })
    useUpdateProfile.mockReturnValue({ mutateAsync: updateProfileMutate, isPending: false })
    useUploadAvatar.mockReturnValue({ mutateAsync: uploadAvatarMutate, isPending: false })
    useDeleteAvatar.mockReturnValue({ mutateAsync: deleteAvatarMutate, isPending: false })
    useSetLeaderboardVisibility.mockReturnValue({ mutateAsync: setVisibilityMutate, isPending: false })
    useUserReports.mockReturnValue({ data: [SAMPLE_REPORT], isPending: false, isError: false })
    useUpdateReport.mockReturnValue({ mutateAsync: updateReportMutate, isPending: false })
    useDeleteReport.mockReturnValue({ mutateAsync: deleteReportMutate, isPending: false })
  })

  it('renders a loading state when the profile query is pending', () => {
    useCurrentUser.mockReturnValue({ data: undefined, isPending: true, isError: false })
    renderPage()
    expect(screen.getByText(/loading your profile/i)).toBeInTheDocument()
  })

  it('renders the user header, stats, and bio when loaded', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Ada Lovelace' })).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByText('USER')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('Mathematician.')).toBeInTheDocument()
  })

  describe('gamification', () => {
    it('renders points, rank, and badge for the current user', () => {
      renderPage()
      expect(screen.getByText('245')).toBeInTheDocument()
      expect(screen.getByText('#12')).toBeInTheDocument()
      // Badge chip uses the human-readable label, not the enum value.
      expect(screen.getByText('Trusted Reporter')).toBeInTheDocument()
    })

    it('shows "Hidden" rank when the user has opted out', () => {
      useCurrentUser.mockReturnValue({
        data: { ...SAMPLE_USER, rank: null, leaderboardHidden: true },
        isPending: false,
        isError: false,
      })
      renderPage()
      expect(screen.getByText('Hidden')).toBeInTheDocument()
    })

    it('shows "—" rank when the user has no rank yet (e.g. fresh account)', () => {
      useCurrentUser.mockReturnValue({
        data: { ...SAMPLE_USER, rank: null, leaderboardHidden: false },
        isPending: false,
        isError: false,
      })
      renderPage()
      expect(screen.getByText('—')).toBeInTheDocument()
    })

    it('omits the badges section when no badges are held', () => {
      useCurrentUser.mockReturnValue({
        data: { ...SAMPLE_USER, badges: [], topBadge: null },
        isPending: false,
        isError: false,
      })
      renderPage()
      expect(screen.queryByRole('heading', { name: /^badges$/i })).not.toBeInTheDocument()
    })

    it('toggles leaderboard visibility and shows a confirmation toast', async () => {
      const user = userEvent.setup()
      setVisibilityMutate.mockResolvedValueOnce({ ...SAMPLE_USER, leaderboardHidden: true })
      renderPage()
      const checkbox = screen.getByRole('checkbox', { name: /hide me from the public leaderboard/i })
      await user.click(checkbox)
      expect(setVisibilityMutate).toHaveBeenCalledWith(true)
      expect(await screen.findByText(/hidden from the public leaderboard/i)).toBeInTheDocument()
    })
  })

  describe('edit profile', () => {
    it('reveals the form with current values when Edit is clicked', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      expect(screen.getByLabelText(/display name/i)).toHaveValue('Ada Lovelace')
      expect(screen.getByLabelText(/^bio$/i)).toHaveValue('Mathematician.')
    })

    it('disables Save when the name is too short', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      const nameInput = screen.getByLabelText(/display name/i)
      await user.clear(nameInput)
      await user.type(nameInput, 'A')
      expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
      expect(screen.getByText(/name must be at least 2/i)).toBeInTheDocument()
    })

    it('saves and shows a success toast on the happy path', async () => {
      const user = userEvent.setup()
      updateProfileMutate.mockResolvedValueOnce({ ...SAMPLE_USER, name: 'Ada L.' })
      renderPage()
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      const nameInput = screen.getByLabelText(/display name/i)
      await user.clear(nameInput)
      await user.type(nameInput, 'Ada L.')
      await user.click(screen.getByRole('button', { name: /^save$/i }))
      expect(updateProfileMutate).toHaveBeenCalledWith({ name: 'Ada L.', bio: 'Mathematician.' })
      expect(await screen.findByText(/profile updated/i)).toBeInTheDocument()
    })

    it('surfaces backend error in the form', async () => {
      const user = userEvent.setup()
      updateProfileMutate.mockRejectedValueOnce(new Error('Name already taken'))
      renderPage()
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      await user.click(screen.getByRole('button', { name: /^save$/i }))
      expect(await screen.findByText(/name already taken/i)).toBeInTheDocument()
    })

    it('Cancel restores the view', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByRole('button', { name: /^edit$/i }))
      await user.click(screen.getByRole('button', { name: /^cancel$/i }))
      expect(screen.queryByLabelText(/display name/i)).not.toBeInTheDocument()
    })
  })

  describe('avatar', () => {
    function makeFile(name, type, size) {
      const file = new File(['a'], name, { type })
      Object.defineProperty(file, 'size', { value: size })
      return file
    }

    it('blocks oversized files client-side without calling the service', async () => {
      const user = userEvent.setup()
      renderPage()
      const input = screen.getByTestId('avatar-file-input')
      await user.upload(input, makeFile('big.jpg', 'image/jpeg', 20 * 1024 * 1024))
      expect(screen.getByText(/15 mb or smaller/i)).toBeInTheDocument()
      expect(uploadAvatarMutate).not.toHaveBeenCalled()
    })

    it('blocks disallowed mime types client-side', () => {
      // userEvent.upload honors the `accept` attribute and silently filters non-matching files,
      // which would skip the validator we want to exercise here. fireEvent.change bypasses that.
      renderPage()
      const input = screen.getByTestId('avatar-file-input')
      fireEvent.change(input, { target: { files: [makeFile('doc.txt', 'text/plain', 10)] } })
      expect(screen.getByText(/jpeg and png images are allowed/i)).toBeInTheDocument()
      expect(uploadAvatarMutate).not.toHaveBeenCalled()
    })

    it('uploads a valid avatar and shows a toast', async () => {
      const user = userEvent.setup()
      uploadAvatarMutate.mockResolvedValueOnce({ avatarUrl: 'https://x/y.jpg' })
      renderPage()
      const input = screen.getByTestId('avatar-file-input')
      await user.upload(input, makeFile('a.jpg', 'image/jpeg', 1024))
      await user.click(screen.getByRole('button', { name: /save avatar/i }))
      expect(uploadAvatarMutate).toHaveBeenCalledTimes(1)
      expect(await screen.findByText(/avatar updated/i)).toBeInTheDocument()
    })

    it('removes the avatar after confirmation', async () => {
      const user = userEvent.setup()
      useCurrentUser.mockReturnValue({
        data: { ...SAMPLE_USER, avatarUrl: 'https://cdn/old.jpg' },
        isPending: false,
        isError: false,
      })
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      deleteAvatarMutate.mockResolvedValueOnce(null)
      renderPage()
      await user.click(screen.getByRole('button', { name: /^remove$/i }))
      expect(deleteAvatarMutate).toHaveBeenCalled()
      expect(await screen.findByText(/avatar removed/i)).toBeInTheDocument()
    })
  })

  describe('my reports section', () => {
    it('shows an empty state when the user has no reports', () => {
      useUserReports.mockReturnValue({ data: [], isPending: false, isError: false })
      renderPage()
      expect(screen.getByText(/haven't submitted any reports yet/i)).toBeInTheDocument()
    })

    it('renders rows with description preview, status, and date', () => {
      renderPage()
      expect(screen.getByText('Broken sidewalk near campus.')).toBeInTheDocument()
      // Status pill uses mapReport's lowercased status mapped to a display label.
      expect(screen.getAllByText('Verified').length).toBeGreaterThan(0)
    })

    it('opens the detail modal when a row is clicked', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByText('Broken sidewalk near campus.'))
      expect(screen.getByRole('dialog', { name: /report details/i })).toBeInTheDocument()
    })

    it('navigates to /?report=ID when "Edit on map" is clicked', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByText('Broken sidewalk near campus.'))
      const dialog = screen.getByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /edit on map/i }))
      expect(screen.getByText('map page')).toBeInTheDocument()
      expect(screen.getByTestId('location-probe')).toHaveTextContent('/?report=100')
    })

    it('deletes a report after confirmation and closes the modal', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      deleteReportMutate.mockResolvedValueOnce(null)
      renderPage()
      await user.click(screen.getByText('Broken sidewalk near campus.'))
      const dialog = screen.getByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))
      expect(deleteReportMutate).toHaveBeenCalledWith(100)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('closes the modal on Escape', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(screen.getByText('Broken sidewalk near campus.'))
      expect(screen.getByRole('dialog')).toBeInTheDocument()
      fireEvent.keyDown(document, { key: 'Escape' })
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })
})
