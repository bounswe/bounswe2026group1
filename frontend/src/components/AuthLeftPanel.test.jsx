import { render, screen } from '@testing-library/react'
import AuthLeftPanel from './AuthLeftPanel.jsx'

describe('AuthLeftPanel', () => {
  it('shows headline and description', () => {
    render(
      <AuthLeftPanel headline="Welcome" description="Join the community." />,
    )
    expect(screen.getByRole('heading', { name: /welcome/i })).toBeInTheDocument()
    expect(screen.getByText(/join the community/i)).toBeInTheDocument()
    expect(screen.getByAltText(/accessible urban path/i)).toBeInTheDocument()
  })
})
