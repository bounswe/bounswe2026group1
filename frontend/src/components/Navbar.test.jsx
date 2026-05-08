import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import Navbar from './Navbar.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))
vi.mock('../hooks/useCurrentUser.js', () => ({
  useCurrentUser: vi.fn(),
}))
vi.mock('./SseStatusIndicator.jsx', () => ({
  default: () => <div data-testid="sse-status" />,
}))

import { useAuth } from '../context/AuthContext.jsx'
import { useCurrentUser } from '../hooks/useCurrentUser.js'

function renderNavbar(initialPath = '/somewhere') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/" element={<><Navbar /><div>home page</div></>} />
        <Route path="/somewhere" element={<Navbar />} />
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/signup" element={<div>signup page</div>} />
        <Route path="/profile" element={<div>profile page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Navbar', () => {
  const logout = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ isAuthenticated: true, isAdmin: false, logout })
    useCurrentUser.mockReturnValue({ data: null })
  })

  describe('when unauthenticated', () => {
    beforeEach(() => {
      useAuth.mockReturnValue({ isAuthenticated: false, isAdmin: false, logout })
    })

    it('does not render the profile menu button', () => {
      renderNavbar()
      expect(screen.queryByRole('button', { name: /open profile menu/i })).not.toBeInTheDocument()
    })

    it('renders Log In and Sign Up links', () => {
      renderNavbar()
      expect(screen.getByRole('link', { name: /log in/i })).toHaveAttribute('href', '/login')
      expect(screen.getByRole('link', { name: /sign up/i })).toHaveAttribute('href', '/signup')
    })
  })

  describe('avatar rendering', () => {
    it('shows the person icon when the user has no avatarUrl', () => {
      useCurrentUser.mockReturnValue({ data: { id: 1, name: 'Ada', avatarUrl: null } })
      renderNavbar()

      const button = screen.getByRole('button', { name: /open profile menu/i })
      expect(button).toBeInTheDocument()
      expect(button.querySelector('img')).toBeNull()
    })

    it('renders the avatar image when avatarUrl is set', () => {
      useCurrentUser.mockReturnValue({
        data: { id: 1, name: 'Ada', avatarUrl: 'https://cdn.example.com/avatar.jpg' },
      })
      renderNavbar()

      const img = screen.getByAltText(/ada's avatar/i)
      expect(img).toHaveAttribute('src', 'https://cdn.example.com/avatar.jpg')
    })

    it('falls back to the icon when the avatar image fails to load', () => {
      useCurrentUser.mockReturnValue({
        data: { id: 1, name: 'Ada', avatarUrl: 'https://cdn.example.com/broken.jpg' },
      })
      renderNavbar()

      const img = screen.getByAltText(/ada's avatar/i)
      fireEvent.error(img)

      expect(screen.queryByAltText(/ada's avatar/i)).not.toBeInTheDocument()
      const button = screen.getByRole('button', { name: /open profile menu/i })
      expect(button.querySelector('img')).toBeNull()
    })
  })

  describe('dropdown menu', () => {
    it('is closed by default', () => {
      renderNavbar()
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /open profile menu/i }),
      ).toHaveAttribute('aria-expanded', 'false')
    })

    it('opens when the avatar button is clicked', async () => {
      const user = userEvent.setup()
      renderNavbar()

      await user.click(screen.getByRole('button', { name: /open profile menu/i }))

      expect(screen.getByRole('menu')).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: /profile/i })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: /log out/i })).toBeInTheDocument()
    })

    it('toggles closed when the avatar button is clicked again', async () => {
      const user = userEvent.setup()
      renderNavbar()

      const button = screen.getByRole('button', { name: /open profile menu/i })
      await user.click(button)
      await user.click(button)

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })

    it('closes when Escape is pressed', async () => {
      const user = userEvent.setup()
      renderNavbar()

      await user.click(screen.getByRole('button', { name: /open profile menu/i }))
      expect(screen.getByRole('menu')).toBeInTheDocument()

      await user.keyboard('{Escape}')

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })

    it('closes when clicking outside the menu', async () => {
      const user = userEvent.setup()
      render(
        <MemoryRouter>
          <Navbar />
          <div data-testid="outside">outside</div>
        </MemoryRouter>,
      )

      await user.click(screen.getByRole('button', { name: /open profile menu/i }))
      expect(screen.getByRole('menu')).toBeInTheDocument()

      fireEvent.pointerDown(screen.getByTestId('outside'))

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
  })

  describe('menu actions', () => {
    it('navigates to /profile when Profile is clicked', async () => {
      const user = userEvent.setup()
      renderNavbar()

      await user.click(screen.getByRole('button', { name: /open profile menu/i }))
      await user.click(screen.getByRole('menuitem', { name: /profile/i }))

      expect(screen.getByText('profile page')).toBeInTheDocument()
    })

    it('calls logout and redirects to / when Log Out is clicked', async () => {
      const user = userEvent.setup()
      renderNavbar()

      await user.click(screen.getByRole('button', { name: /open profile menu/i }))
      await user.click(screen.getByRole('menuitem', { name: /log out/i }))

      expect(logout).toHaveBeenCalledTimes(1)
      expect(screen.getByText('home page')).toBeInTheDocument()
    })
  })
})
