import { render, screen, act } from '@testing-library/react'
import Toast from './Toast.jsx'

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the message', () => {
    render(<Toast message="Saved!" onDismiss={() => {}} />)
    expect(screen.getByText('Saved!')).toBeInTheDocument()
  })

  it('calls onDismiss after duration plus exit animation delay', () => {
    const onDismiss = vi.fn()
    render(<Toast message="Hi" duration={1000} onDismiss={onDismiss} />)

    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(onDismiss).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })
})
