import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import PresetDetailsModal from './PresetDetailsModal.jsx'

const CATALOG = [
  { name: 'AVOID_STAIRS', label: 'Avoid stairs', description: 'Skips steps.' },
  { name: 'REQUIRE_RAMPS', label: 'Need ramps', description: 'Prefers ramps.' },
  { name: 'AVOID_SLIPPERY_SURFACES', label: 'Slippery', description: 'Multi-surface rule.' },
]

describe('PresetDetailsModal', () => {
  const onClose = vi.fn()
  const onEdit = vi.fn()
  const onDelete = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders empty preset message and closes', async () => {
    const user = userEvent.setup()
    render(
      <PresetDetailsModal
        title="Empty"
        subtitle="Built-in"
        selectedConstraints={[]}
        allConstraints={CATALOG}
        travelMode="WALKING"
        onClose={onClose}
      />,
    )
    expect(screen.getByText(/no constraints/i)).toBeInTheDocument()
    const closeButtons = screen.getAllByRole('button', { name: /^close$/i })
    await user.click(closeButtons[closeButtons.length - 1])
    expect(onClose).toHaveBeenCalled()
  })

  it('groups selected constraints and shows travel mode', () => {
    render(
      <PresetDetailsModal
        title="Tester"
        subtitle="Custom"
        selectedConstraints={['AVOID_STAIRS', 'REQUIRE_RAMPS', 'AVOID_SLIPPERY_SURFACES']}
        allConstraints={CATALOG}
        travelMode="WHEELCHAIR"
        onClose={onClose}
      />,
    )
    expect(screen.getByText('Stairs')).toBeInTheDocument()
    expect(screen.getByText('Ramps')).toBeInTheDocument()
    expect(screen.getByText(/wheelchair/i)).toBeInTheDocument()
  })

  it('confirms delete in two steps', async () => {
    const user = userEvent.setup()
    render(
      <PresetDetailsModal
        title="To delete"
        subtitle={null}
        selectedConstraints={['AVOID_STAIRS']}
        allConstraints={CATALOG}
        travelMode={null}
        onClose={onClose}
        onDelete={onDelete}
        onEdit={onEdit}
      />,
    )

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await user.click(screen.getByRole('button', { name: /confirm delete/i }))
    expect(onDelete).toHaveBeenCalled()
  })
})
