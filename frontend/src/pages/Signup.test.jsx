import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Signup from './Signup.jsx'

describe('Signup page', () => {
  describe('rendering', () => {
    it('renders the "Join Mapcess" heading', () => {
      render(<Signup />)
      expect(screen.getByRole('heading', { name: /join mapcess/i })).toBeInTheDocument()
    })

    it('renders a full name input', () => {
      render(<Signup />)
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    })

    it('renders an email input', () => {
      render(<Signup />)
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    })

    it('renders a password input', () => {
      render(<Signup />)
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    })

    it('renders the Create Account submit button', () => {
      render(<Signup />)
      expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument()
    })

    it('renders the terms & privacy checkbox', () => {
      render(<Signup />)
      expect(screen.getByRole('checkbox', { name: /terms of service/i })).toBeInTheDocument()
    })

    it('renders Google and Apple social auth buttons', () => {
      render(<Signup />)
      expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /apple/i })).toBeInTheDocument()
    })

    it('renders a link to the login page', () => {
      render(<Signup />)
      expect(screen.getByRole('link', { name: /log in/i })).toHaveAttribute('href', '/login')
    })
  })

  describe('form inputs', () => {
    it('full name input accepts typed text', async () => {
      const user = userEvent.setup()
      render(<Signup />)
      const nameInput = screen.getByLabelText(/full name/i)
      await user.type(nameInput, 'Alex Rivera')
      expect(nameInput).toHaveValue('Alex Rivera')
    })

    it('email input accepts typed text', async () => {
      const user = userEvent.setup()
      render(<Signup />)
      const emailInput = screen.getByLabelText(/email address/i)
      await user.type(emailInput, 'alex@example.com')
      expect(emailInput).toHaveValue('alex@example.com')
    })

    it('password input is hidden by default', () => {
      render(<Signup />)
      expect(screen.getByLabelText(/^password$/i)).toHaveAttribute('type', 'password')
    })

    it('terms checkbox is unchecked by default', () => {
      render(<Signup />)
      expect(screen.getByRole('checkbox', { name: /terms of service/i })).not.toBeChecked()
    })

    it('terms checkbox can be checked', async () => {
      const user = userEvent.setup()
      render(<Signup />)
      const checkbox = screen.getByRole('checkbox', { name: /terms of service/i })
      await user.click(checkbox)
      expect(checkbox).toBeChecked()
    })
  })
})
