import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../context/AuthContext.jsx'
import Login from './Login.jsx'
import * as authService from '../services/authService.js'

vi.mock('../services/authService.js')

const mockNavigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

function renderLogin({ initialEntries } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Routes>
            <Route path="/" element={<Login />} />
            <Route path="/login" element={<Login />} />
          </Routes>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('Login page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ─── Rendering ───────────────────────────────────────────────────────────────

  describe('rendering', () => {
    it('renders the "Welcome back" heading', () => {
      renderLogin()
      expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
    })

    it('renders an email input', () => {
      renderLogin()
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    })

    it('renders a password input', () => {
      renderLogin()
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    })

    it('renders the Sign In submit button', () => {
      renderLogin()
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    })

    // Commented out: Google/Apple OAuth buttons removed from the app.
    // See: https://github.com/bounswe/bounswe2026group1/issues/133
    // it('renders Google and Apple social auth buttons', () => {
    //   renderLogin()
    //   expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
    //   expect(screen.getByRole('button', { name: /apple/i })).toBeInTheDocument()
    // })

    it('renders a link to the signup page', () => {
      renderLogin()
      expect(screen.getByRole('link', { name: /create an account/i })).toHaveAttribute(
        'href',
        '/signup',
      )
    })
  })

  // ─── Password visibility toggle ──────────────────────────────────────────────

  describe('password visibility toggle', () => {
    it('password field is hidden by default', () => {
      renderLogin()
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })

    it('clicking the toggle reveals the password', async () => {
      const user = userEvent.setup()
      renderLogin()
      await user.click(screen.getByRole('button', { name: /show password/i }))
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'text')
    })

    it('clicking the toggle again hides the password', async () => {
      const user = userEvent.setup()
      renderLogin()
      await user.click(screen.getByRole('button', { name: /show password/i }))
      await user.click(screen.getByRole('button', { name: /hide password/i }))
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })
  })

  // ─── Controlled inputs ───────────────────────────────────────────────────────

  describe('form inputs', () => {
    it('email input accepts typed text', async () => {
      const user = userEvent.setup()
      renderLogin()
      const emailInput = screen.getByLabelText(/email address/i)
      await user.type(emailInput, 'test@example.com')
      expect(emailInput).toHaveValue('test@example.com')
    })

    it('password input accepts typed text', async () => {
      const user = userEvent.setup()
      renderLogin()
      const passwordInput = screen.getByLabelText(/^password$/i)
      await user.type(passwordInput, 'secret123')
      expect(passwordInput).toHaveValue('secret123')
    })
  })

  // ─── Form submission (integration) ───────────────────────────────────────────

  describe('form submission', () => {
    it('calls loginUser with the entered email and password', async () => {
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'secret')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => {
        expect(authService.loginUser).toHaveBeenCalledWith({
          email: 'a@b.com',
          password: 'secret',
        })
      })
    })

    it('navigates to / on successful login when no redirect is pending', async () => {
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'secret')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true }))
    })

    it('returns to the protected route stashed by RequireAuth on successful login', async () => {
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderLogin({
        initialEntries: [
          { pathname: '/login', state: { from: { pathname: '/profile' } } },
        ],
      })

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'secret')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() =>
        expect(mockNavigate).toHaveBeenCalledWith('/profile', { replace: true }),
      )
    })

    it('stores the JWT token in localStorage on successful login', async () => {
      authService.loginUser.mockResolvedValue({ token: 'my-jwt' })
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'secret')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => expect(localStorage.getItem('token')).toBe('my-jwt'))
    })

    it('shows an error message on failed login', async () => {
      authService.loginUser.mockRejectedValue(Object.assign(new Error('Unauthorized'), { status: 401 }))
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'wrong')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => {
        expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument()
      })
    })

    it('does not navigate on failed login', async () => {
      authService.loginUser.mockRejectedValue(Object.assign(new Error('Unauthorized'), { status: 401 }))
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'wrong')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => expect(mockNavigate).not.toHaveBeenCalled())
    })

    it('clears a previous error when resubmitting', async () => {
      authService.loginUser
        .mockRejectedValueOnce(Object.assign(new Error('Unauthorized'), { status: 401 }))
        .mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'wrong')
      await user.click(screen.getByRole('button', { name: /sign in/i }))
      await screen.findByText(/invalid email or password/i)

      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => {
        expect(screen.queryByText(/invalid email or password/i)).not.toBeInTheDocument()
      })
    })

    it('disables the button and shows loading text while submitting', async () => {
      // Never-resolving promise keeps the component in the loading state for the
      // duration of the test, avoiding state updates outside act().
      authService.loginUser.mockReturnValue(new Promise(() => {}))
      const user = userEvent.setup()
      renderLogin()

      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'secret')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled()
    })
  })
})
