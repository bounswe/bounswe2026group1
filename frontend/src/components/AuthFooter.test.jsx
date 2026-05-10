import { render, screen } from '@testing-library/react'
import AuthFooter from './AuthFooter.jsx'

describe('AuthFooter', () => {
  it('renders branding and footer links', () => {
    render(<AuthFooter />)
    expect(screen.getByText('Mapcess')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /privacy policy/i })).toHaveAttribute('href', '#')
    expect(screen.getByText(/© 2025 mapcess/i)).toBeInTheDocument()
  })
})
