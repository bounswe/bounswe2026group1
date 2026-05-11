import { render, screen } from '@testing-library/react'
import Badge from './Badge.jsx'

describe('Badge', () => {
  it('renders children', () => {
    render(<Badge>Active</Badge>)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('defaults to neutral tone', () => {
    const { container } = render(<Badge>Tag</Badge>)
    const span = container.querySelector('span')
    expect(span?.className).toMatch(/bg-surface-container/)
  })

  it('applies tone-specific classes', () => {
    const { rerender, container } = render(<Badge tone="success">Ok</Badge>)
    expect(container.querySelector('span')?.className).toMatch(/primary-container/)

    rerender(<Badge tone="danger">Bad</Badge>)
    expect(container.querySelector('span')?.className).toMatch(/error-container/)
  })
})
