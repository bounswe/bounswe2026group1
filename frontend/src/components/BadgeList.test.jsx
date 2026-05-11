import { render, screen } from '@testing-library/react'
import BadgeList from './BadgeList.jsx'

describe('BadgeList', () => {
  it('renders nothing when badges missing or empty', () => {
    const { container: a } = render(<BadgeList badges={null} />)
    expect(a.firstChild).toBeNull()
    const { container: b } = render(<BadgeList badges={[]} />)
    expect(b.firstChild).toBeNull()
  })

  it('lists badges in a labelled region', () => {
    render(<BadgeList badges={['TOP_10', 'EXPERT_MAPPER']} />)
    expect(screen.getByLabelText(/user badges/i)).toBeInTheDocument()
    expect(screen.getByText('Top 10')).toBeInTheDocument()
    expect(screen.getByText('Expert Mapper')).toBeInTheDocument()
  })
})
