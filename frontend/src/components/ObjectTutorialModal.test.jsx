import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ObjectTutorialModal from './ObjectTutorialModal.jsx'

describe('ObjectTutorialModal', () => {
  let onClose, user

  beforeEach(() => {
    onClose = vi.fn()
    user = userEvent.setup()
  })

  it('renders the type label and every measurement for RAMP', () => {
    render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    expect(screen.getByRole('dialog', { name: /how to measure ramp/i })).toBeInTheDocument()
    // Three RAMP measurements: Slope, Width, Height
    expect(screen.getByText('Slope')).toBeInTheDocument()
    expect(screen.getByText('Width')).toBeInTheDocument()
    expect(screen.getByText('Height')).toBeInTheDocument()
  })

  it('renders threshold pills for measurements that define them', () => {
    render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    expect(screen.getByText(/≤ 10 % is accessible/i)).toBeInTheDocument()
    expect(screen.getByText(/≥ 100 cm is accessible/i)).toBeInTheDocument()
  })

  it('renders a min-max range when both thresholds exist', () => {
    render(<ObjectTutorialModal objectType="ROOM_SIGN" onClose={onClose} />)
    expect(screen.getByText(/120–160 cm is accessible/i)).toBeInTheDocument()
  })

  it('swaps content when the objectType prop changes', () => {
    const { rerender } = render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    expect(screen.getByText('Slope')).toBeInTheDocument()
    rerender(<ObjectTutorialModal objectType="ELEVATOR" onClose={onClose} />)
    expect(screen.queryByText('Slope')).not.toBeInTheDocument()
    expect(screen.getByText('Door Width')).toBeInTheDocument()
    expect(screen.getByText('Cabin Width')).toBeInTheDocument()
  })

  it('renders nothing for an unknown objectType', () => {
    const { container } = render(<ObjectTutorialModal objectType="NOPE" onClose={onClose} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('closes via the explicit Close button', async () => {
    render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: /close tutorial/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes when Escape is pressed', async () => {
    render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes when the backdrop is clicked', async () => {
    render(<ObjectTutorialModal objectType="RAMP" onClose={onClose} />)
    await user.click(screen.getByRole('dialog', { name: /how to measure ramp/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
