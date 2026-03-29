import { render, screen } from '@testing-library/react'
import SocialAuthButtons from './SocialAuthButtons.jsx'

describe('SocialAuthButtons', () => {
  it('renders Google button', () => {
    render(<SocialAuthButtons />)
    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
  })

  it('renders Apple button', () => {
    render(<SocialAuthButtons />)
    expect(screen.getByRole('button', { name: /apple/i })).toBeInTheDocument()
  })

  it('both buttons are type="button" so they do not submit forms', () => {
    render(<SocialAuthButtons />)
    const buttons = screen.getAllByRole('button')
    buttons.forEach((btn) => expect(btn).toHaveAttribute('type', 'button'))
  })
})
