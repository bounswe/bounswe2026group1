import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Login from './Login.jsx'

describe('Login page', () => {
  describe('rendering', () => {
    it('renders the "Welcome back" heading', () => {
      render(<Login />)
      expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
    })

    it('renders an email input', () => {
      render(<Login />)
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    })

    it('renders a password input', () => {
      render(<Login />)
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    })

    it('renders the Sign In submit button', () => {
      render(<Login />)
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    })

    it('renders a "Forgot password?" link', () => {
      render(<Login />)
      expect(screen.getByRole('link', { name: /forgot password/i })).toBeInTheDocument()
    })

    it('renders Google and Apple social auth buttons', () => {
      render(<Login />)
      expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /apple/i })).toBeInTheDocument()
    })

    it('renders a link to the signup page', () => {
      render(<Login />)
      expect(screen.getByRole('link', { name: /create an account/i })).toHaveAttribute(
        'href',
        '/signup',
      )
    })
  })

  describe('password visibility toggle', () => {
    it('password field is hidden by default', () => {
      render(<Login />)
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })

    it('clicking the toggle reveals the password', async () => {
      const user = userEvent.setup()
      render(<Login />)
      const toggle = screen.getByRole('button', { name: /show password/i })
      await user.click(toggle)
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'text')
    })

    it('clicking the toggle again hides the password', async () => {
      const user = userEvent.setup()
      render(<Login />)
      const toggle = screen.getByRole('button', { name: /show password/i })
      await user.click(toggle)
      await user.click(screen.getByRole('button', { name: /hide password/i }))
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })
  })

  describe('form inputs', () => {
    it('email input accepts typed text', async () => {
      const user = userEvent.setup()
      render(<Login />)
      const emailInput = screen.getByLabelText(/email address/i)
      await user.type(emailInput, 'test@example.com')
      expect(emailInput).toHaveValue('test@example.com')
    })

    it('password input accepts typed text', async () => {
      const user = userEvent.setup()
      render(<Login />)
      const passwordInput = screen.getByLabelText(/^password$/i)
      await user.type(passwordInput, 'secret123')
      expect(passwordInput).toHaveValue('secret123')
    })
  })
})
