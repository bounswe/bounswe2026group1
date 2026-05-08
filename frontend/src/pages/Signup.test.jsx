import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../context/AuthContext.jsx'
import Signup from './Signup.jsx'
import * as authService from '../services/authService.js'

vi.mock('../services/authService.js')

const mockNavigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

function renderSignup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Signup />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

/** Fill all fields and click submit. Terms checkbox must be checked to enable the button. */
async function fillAndSubmit(user, overrides = {}) {
  const name = overrides.name ?? 'Alex Rivera'
  const email = overrides.email ?? 'alex@example.com'
  const password = overrides.password ?? 'Secret12!'

  await user.type(screen.getByLabelText(/full name/i), name)
  await user.type(screen.getByLabelText(/email address/i), email)
  await user.type(screen.getByLabelText(/^password$/i), password)
  await user.click(screen.getByRole('checkbox', { name: /terms of service/i }))
  await user.click(screen.getByRole('button', { name: /create account/i }))
}

describe('Signup page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ─── Rendering ───────────────────────────────────────────────────────────────

  describe('rendering', () => {
    it('renders the "Join Mapcess" heading', () => {
      renderSignup()
      expect(screen.getByRole('heading', { name: /join mapcess/i })).toBeInTheDocument()
    })

    it('renders a full name input', () => {
      renderSignup()
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    })

    it('renders an email input', () => {
      renderSignup()
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    })

    it('renders a password input', () => {
      renderSignup()
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    })

    it('renders the Create Account submit button', () => {
      renderSignup()
      expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument()
    })

    it('renders the terms & privacy checkbox', () => {
      renderSignup()
      expect(screen.getByRole('checkbox', { name: /terms of service/i })).toBeInTheDocument()
    })

    // Commented out: Google/Apple OAuth buttons removed from the app.
    // See: https://github.com/bounswe/bounswe2026group1/issues/133
    // it('renders Google and Apple social auth buttons', () => {
    //   renderSignup()
    //   expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
    //   expect(screen.getByRole('button', { name: /apple/i })).toBeInTheDocument()
    // })

    it('renders a link to the login page', () => {
      renderSignup()
      expect(screen.getByRole('link', { name: /log in/i })).toHaveAttribute('href', '/login')
    })
  })

  // ─── Controlled inputs ───────────────────────────────────────────────────────

  describe('form inputs', () => {
    it('full name input accepts typed text', async () => {
      const user = userEvent.setup()
      renderSignup()
      const nameInput = screen.getByLabelText(/full name/i)
      await user.type(nameInput, 'Alex Rivera')
      expect(nameInput).toHaveValue('Alex Rivera')
    })

    it('email input accepts typed text', async () => {
      const user = userEvent.setup()
      renderSignup()
      const emailInput = screen.getByLabelText(/email address/i)
      await user.type(emailInput, 'alex@example.com')
      expect(emailInput).toHaveValue('alex@example.com')
    })

    it('password input is hidden by default', () => {
      renderSignup()
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })

    it('terms checkbox is unchecked by default', () => {
      renderSignup()
      expect(screen.getByRole('checkbox', { name: /terms of service/i })).not.toBeChecked()
    })

    it('terms checkbox can be checked', async () => {
      const user = userEvent.setup()
      renderSignup()
      await user.click(screen.getByRole('checkbox', { name: /terms of service/i }))
      expect(screen.getByRole('checkbox', { name: /terms of service/i })).toBeChecked()
    })
  })

  // ─── Submit button guard ──────────────────────────────────────────────────────

  describe('submit button', () => {
    it('is disabled when terms are not accepted', () => {
      renderSignup()
      expect(screen.getByRole('button', { name: /create account/i })).toBeDisabled()
    })

    it('is enabled after accepting terms and entering a valid password', async () => {
      const user = userEvent.setup()
      renderSignup()
      await user.type(screen.getByLabelText(/^password$/i), 'Secret12!')
      await user.click(screen.getByRole('checkbox', { name: /terms of service/i }))
      expect(screen.getByRole('button', { name: /create account/i })).not.toBeDisabled()
    })

    it('stays disabled when password does not meet all rules', async () => {
      const user = userEvent.setup()
      renderSignup()
      await user.type(screen.getByLabelText(/^password$/i), 'weakpass')
      await user.click(screen.getByRole('checkbox', { name: /terms of service/i }))
      expect(screen.getByRole('button', { name: /create account/i })).toBeDisabled()
    })
  })

  // ─── Password validation UI ──────────────────────────────────────────────────

  describe('password requirements checklist', () => {
    it('shows a descriptive message for each unmet rule as the user types', async () => {
      const user = userEvent.setup()
      renderSignup()
      await user.type(screen.getByLabelText(/^password$/i), 'abc')

      expect(screen.getByText(/at least 8 characters long/i)).toBeInTheDocument()
      expect(screen.getByText(/uppercase letter/i)).toBeInTheDocument()
      expect(screen.getByText(/at least one number/i)).toBeInTheDocument()
      expect(screen.getByText(/special character/i)).toBeInTheDocument()
    })

    it('marks rules as passed when satisfied', async () => {
      const user = userEvent.setup()
      renderSignup()
      await user.type(screen.getByLabelText(/^password$/i), 'Secret12!')

      for (const id of ['length', 'uppercase', 'lowercase', 'digit', 'special']) {
        expect(screen.getByTestId(`password-rule-${id}`)).toHaveAttribute(
          'data-passed',
          'true',
        )
      }
    })

    it('does not submit when the password is invalid', async () => {
      authService.registerUser.mockResolvedValue({ id: 1 })
      const user = userEvent.setup()
      renderSignup()

      await user.type(screen.getByLabelText(/full name/i), 'Alex')
      await user.type(screen.getByLabelText(/email address/i), 'a@b.com')
      await user.type(screen.getByLabelText(/^password$/i), 'weakpass')
      await user.click(screen.getByRole('checkbox', { name: /terms of service/i }))
      // Button is disabled; simulate an Enter submit on the form instead.
      await user.keyboard('{Enter}')

      expect(authService.registerUser).not.toHaveBeenCalled()
    })
  })

  // ─── Form submission (integration) ───────────────────────────────────────────

  describe('form submission', () => {
    it('calls registerUser with the entered name, email and password', async () => {
      authService.registerUser.mockResolvedValue({ id: 1, name: 'Alex Rivera', email: 'alex@example.com', role: 'USER' })
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => {
        expect(authService.registerUser).toHaveBeenCalledWith({
          name: 'Alex Rivera',
          email: 'alex@example.com',
          password: 'Secret12!',
        })
      })
    })

    it('calls loginUser with the same credentials after registration', async () => {
      authService.registerUser.mockResolvedValue({ id: 1, name: 'Alex Rivera', email: 'alex@example.com', role: 'USER' })
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => {
        expect(authService.loginUser).toHaveBeenCalledWith({
          email: 'alex@example.com',
          password: 'Secret12!',
        })
      })
    })

    it('navigates to / on successful signup', async () => {
      authService.registerUser.mockResolvedValue({ id: 1 })
      authService.loginUser.mockResolvedValue({ token: 'tok' })
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })

    it('stores the JWT token in localStorage on successful signup', async () => {
      authService.registerUser.mockResolvedValue({ id: 1 })
      authService.loginUser.mockResolvedValue({ token: 'my-jwt' })
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => expect(localStorage.getItem('token')).toBe('my-jwt'))
    })

    it('shows a duplicate-email error when the backend returns 409', async () => {
      authService.registerUser.mockRejectedValue(new Error('Email already in use'))
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => {
        expect(screen.getByText(/already exists/i)).toBeInTheDocument()
      })
    })

    it('shows the backend error message for other failures', async () => {
      authService.registerUser.mockRejectedValue(new Error('Password is too weak'))
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => {
        expect(screen.getByText(/password is too weak/i)).toBeInTheDocument()
      })
    })

    it('does not navigate on failed signup', async () => {
      authService.registerUser.mockRejectedValue(new Error('Email already in use'))
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      await waitFor(() => expect(mockNavigate).not.toHaveBeenCalled())
    })

    it('disables the button and shows loading text while submitting', async () => {
      // Never-resolving promise keeps the component in the loading state for the
      // duration of the test, avoiding state updates outside act().
      authService.registerUser.mockReturnValue(new Promise(() => {}))
      const user = userEvent.setup()
      renderSignup()

      await fillAndSubmit(user)

      expect(screen.getByRole('button', { name: /creating account/i })).toBeDisabled()
    })
  })
})
