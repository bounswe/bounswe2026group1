import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import OnboardingTutorial from './OnboardingTutorial.jsx'

describe('OnboardingTutorial', () => {
  it('renders the welcome slide first with a dialog role and a labelled title', () => {
    const onClose = vi.fn()
    render(<OnboardingTutorial onClose={onClose} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: /welcome to mapcess/i }),
    ).toBeInTheDocument()
  })

  it('advances through every slide via the Next button and surfaces the categories', async () => {
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={vi.fn()} />)

    const titles = ['Ramps', 'Sidewalks', 'Elevators', 'Doors', 'How to file a report']
    for (const title of titles) {
      await user.click(screen.getByRole('button', { name: /next/i }))
      expect(screen.getByRole('heading', { name: new RegExp(title, 'i') })).toBeInTheDocument()
    }
  })

  it('calls onClose when the Skip button is pressed', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={onClose} />)

    await user.click(screen.getByRole('button', { name: /skip tour/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('replaces Next with "Got it" on the final slide and calls onClose when pressed', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={onClose} />)

    // Five "Next" clicks gets us from slide 1 (Welcome) to slide 6 (How to file…)
    for (let i = 0; i < 5; i++) {
      await user.click(screen.getByRole('button', { name: /next/i }))
    }

    expect(screen.queryByRole('button', { name: /^next$/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /got it/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes when Escape is pressed', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={onClose} />)

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes when the backdrop is clicked but not when the modal body is clicked', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={onClose} />)

    // Clicking on the title (inside the modal body) must not close the dialog
    await user.click(screen.getByRole('heading', { name: /welcome to mapcess/i }))
    expect(onClose).not.toHaveBeenCalled()

    // Clicking on the dialog backdrop itself must close
    await user.click(screen.getByRole('dialog'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('navigates with arrow keys', async () => {
    const user = userEvent.setup()
    render(<OnboardingTutorial onClose={vi.fn()} />)

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('heading', { name: /ramps/i })).toBeInTheDocument()

    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('heading', { name: /welcome to mapcess/i })).toBeInTheDocument()
  })
})
