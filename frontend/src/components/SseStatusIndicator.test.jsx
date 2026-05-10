import { render, screen } from '@testing-library/react'
import SseStatusIndicator from './SseStatusIndicator.jsx'

vi.mock('../context/SseContext.jsx', () => ({
  useSseStatus: vi.fn(),
}))

import { useSseStatus } from '../context/SseContext.jsx'

describe('SseStatusIndicator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when connected', () => {
    useSseStatus.mockReturnValue('connected')
    const { container } = render(<SseStatusIndicator />)
    expect(container.firstChild).toBeNull()
  })

  it('shows offline when disconnected', () => {
    useSseStatus.mockReturnValue('disconnected')
    render(<SseStatusIndicator />)
    expect(screen.getByText(/offline/i)).toBeInTheDocument()
  })

  it('shows reconnecting state', () => {
    useSseStatus.mockReturnValue('reconnecting')
    render(<SseStatusIndicator />)
    expect(screen.getByText(/reconnecting/i)).toBeInTheDocument()
  })
})
