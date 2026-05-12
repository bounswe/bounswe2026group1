import { render, screen } from '@testing-library/react'
import BadgeIcon from './BadgeIcon.jsx'

describe('BadgeIcon', () => {
  it('renders nothing for unknown badge', () => {
    const { container } = render(<BadgeIcon badge="UNKNOWN_BADGE" />)
    expect(container.firstChild).toBeNull()
  })

  it('renders md chip with label', () => {
    render(<BadgeIcon badge="TOP_10" />)
    expect(screen.getByText('Top 10')).toBeInTheDocument()
  })

  it('renders sm size with aria-label only', () => {
    render(<BadgeIcon badge="TRUSTED_REPORTER" size="sm" />)
    expect(screen.getByLabelText(/trusted reporter/i)).toBeInTheDocument()
    expect(screen.queryByText('Trusted Reporter')).not.toBeInTheDocument()
  })
})
