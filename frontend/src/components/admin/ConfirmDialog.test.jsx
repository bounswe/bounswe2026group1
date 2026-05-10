import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ConfirmDialog from './ConfirmDialog.jsx'

describe('ConfirmDialog', () => {
  it('renders nothing when open is false', () => {
    const { container } = render(
      <ConfirmDialog open={false} title="T" onConfirm={() => {}} onCancel={() => {}} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders title, message, and default button labels when open', () => {
    render(
      <ConfirmDialog
        open
        title="Delete item?"
        message="This cannot be undone."
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /delete item/i })).toBeInTheDocument()
    expect(screen.getByText(/this cannot be undone/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^confirm$/i })).toBeInTheDocument()
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    render(
      <ConfirmDialog open title="T" onConfirm={() => {}} onCancel={onCancel} />,
    )
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('calls onConfirm when Confirm is clicked', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog open title="T" onConfirm={onConfirm} onCancel={() => {}} />,
    )
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('uses custom confirm and cancel labels', () => {
    render(
      <ConfirmDialog
        open
        title="T"
        confirmLabel="Yes, delete"
        cancelLabel="Go back"
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /yes, delete/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /go back/i })).toBeInTheDocument()
  })

  it('shows Working… and disables buttons when busy', () => {
    render(
      <ConfirmDialog
        open
        title="T"
        busy
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /working/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
  })
})
