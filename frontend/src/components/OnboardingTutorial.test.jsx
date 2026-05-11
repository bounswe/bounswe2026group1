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

    const titles = [
      /^ramps$/i,
      /ramps — landings & stairs/i,
      /curb cuts/i,
      /sidewalks/i,
      /elevators — cabin/i,
      /elevators — entrance & panel/i,
      /doors — glass & contrast/i,
      /doors — handles/i,
      /how to file a report/i,
    ]
    for (const title of titles) {
      await user.click(screen.getByRole('button', { name: /next/i }))
      expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
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

    // Nine "Next" clicks gets us from slide 1 (Welcome) to slide 10 (How to file…)
    for (let i = 0; i < 9; i++) {
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
    expect(screen.getByRole('heading', { name: /^ramps$/i })).toBeInTheDocument()

    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('heading', { name: /welcome to mapcess/i })).toBeInTheDocument()
  })

  it('cites the Belir guide as the diagram source', () => {
    render(<OnboardingTutorial onClose={vi.fn()} />)
    // Two mentions on the welcome slide: the introductory tip and the
    // persistent attribution above the footer.
    expect(screen.getAllByText(/özlem belir/i).length).toBeGreaterThan(0)
    expect(
      screen.getAllByText(/mimari erişilebilirlik kılavuzu/i).length,
    ).toBeGreaterThan(0)
  })
})
