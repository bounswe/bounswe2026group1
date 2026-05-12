import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CustomPresetEditorModal from './CustomPresetEditorModal.jsx'

const CATALOG = [
  { name: 'AVOID_STAIRS', label: 'Avoid stairs', description: 'Skips steps.' },
  { name: 'REQUIRE_RAMPS', label: 'Need ramps', description: 'Prefers ramps.' },
]

describe('CustomPresetEditorModal', () => {
  const onClose = vi.fn()
  const onSave = vi.fn()
  const onDelete = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    onSave.mockResolvedValue(undefined)
    onDelete.mockResolvedValue(undefined)
  })

  it('disables save until name is valid', () => {
    onSave.mockReset()
    render(
      <CustomPresetEditorModal
        mode="create"
        profile={null}
        availableConstraints={CATALOG}
        onClose={onClose}
        onSave={onSave}
        busy={false}
        errorMessage={null}
      />,
    )

    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
    expect(screen.getByText(/required \(1–50 characters\)/i)).toBeInTheDocument()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('submits trimmed name and constraints', async () => {
    const user = userEvent.setup()
    render(
      <CustomPresetEditorModal
        mode="create"
        profile={null}
        availableConstraints={CATALOG}
        onClose={onClose}
        onSave={onSave}
        busy={false}
        errorMessage={null}
      />,
    )

    await user.type(screen.getByLabelText(/preset name/i), 'Commute')
    await user.click(screen.getByLabelText(/avoid stairs/i))
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(onSave).toHaveBeenCalledWith({
      name: 'Commute',
      constraints: ['AVOID_STAIRS'],
      preferredTravelMode: 'WALKING',
    })
  })

  it('delete flow in edit mode calls onDelete', async () => {
    const user = userEvent.setup()
    render(
      <CustomPresetEditorModal
        mode="edit"
        profile={{ name: 'Work', constraints: ['AVOID_STAIRS'], preferredTravelMode: 'WALKING' }}
        availableConstraints={CATALOG}
        onClose={onClose}
        onSave={onSave}
        onDelete={onDelete}
        busy={false}
        errorMessage={null}
      />,
    )

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await user.click(screen.getByRole('button', { name: /confirm delete/i }))
    expect(onDelete).toHaveBeenCalled()
  })

  it('surfaces server errorMessage', () => {
    render(
      <CustomPresetEditorModal
        mode="create"
        profile={null}
        availableConstraints={CATALOG}
        onClose={onClose}
        onSave={onSave}
        busy={false}
        errorMessage="Server down"
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Server down')
  })
})
