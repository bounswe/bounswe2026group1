import { render, screen } from '@testing-library/react'
import StatCard from './StatCard.jsx'

describe('StatCard', () => {
  it('renders label and value', () => {
    render(<StatCard label="Users" value={42} icon="group" />)
    expect(screen.getByText('Users')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('shows em dash when value is null', () => {
    render(<StatCard label="X" value={null} icon="flag" />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('supports error accent', () => {
    const { container } = render(
      <StatCard label="Banned" value={1} icon="block" accent="error" />,
    )
    expect(container.querySelector('.bg-error-container')).toBeTruthy()
  })
})
