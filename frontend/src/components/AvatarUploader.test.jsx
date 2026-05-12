import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AvatarUploader from './AvatarUploader.jsx'

describe('AvatarUploader', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows Upload avatar when there is no current image', () => {
    render(
      <AvatarUploader
        currentAvatarUrl={null}
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: /upload avatar/i })).toBeInTheDocument()
  })

  it('shows validation error for disallowed file types', async () => {
    const user = userEvent.setup()
    render(
      <AvatarUploader
        currentAvatarUrl={null}
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const file = new File(['x'], 'pic.gif', { type: 'image/gif' })
    const input = screen.getByTestId('avatar-file-input')
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByRole('alert')).toHaveTextContent(/jpeg and png/i)
  })

  it('shows validation error when file exceeds size limit', async () => {
    const user = userEvent.setup()
    render(
      <AvatarUploader
        currentAvatarUrl={null}
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const file = new File([new Uint8Array(16 * 1024 * 1024 + 1)], 'big.jpg', {
      type: 'image/jpeg',
    })
    await user.upload(screen.getByTestId('avatar-file-input'), file)
    expect(await screen.findByRole('alert')).toHaveTextContent(/15 mb/i)
  })

  it('calls onUpload after choosing a valid file and clicking Save', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn().mockResolvedValue(undefined)
    render(
      <AvatarUploader
        currentAvatarUrl={null}
        isUploading={false}
        isDeleting={false}
        onUpload={onUpload}
        onDelete={vi.fn()}
      />,
    )
    const file = new File(['x'], 'pic.jpg', { type: 'image/jpeg' })
    await user.upload(screen.getByTestId('avatar-file-input'), file)
    await user.click(screen.getByRole('button', { name: /save avatar/i }))
    await waitFor(() => expect(onUpload).toHaveBeenCalledWith(file))
  })

  it('surfaces upload errors from onUpload', async () => {
    const user = userEvent.setup()
    const onUpload = vi.fn().mockRejectedValue(new Error('Server busy'))
    render(
      <AvatarUploader
        currentAvatarUrl={null}
        isUploading={false}
        isDeleting={false}
        onUpload={onUpload}
        onDelete={vi.fn()}
      />,
    )
    await user.upload(screen.getByTestId('avatar-file-input'), new File(['x'], 'p.jpg', { type: 'image/jpeg' }))
    await user.click(screen.getByRole('button', { name: /save avatar/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/server busy/i)
  })

  it('calls onDelete when Remove is confirmed', async () => {
    const user = userEvent.setup()
    const onDelete = vi.fn().mockResolvedValue(undefined)
    render(
      <AvatarUploader
        currentAvatarUrl="https://example.com/a.png"
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={onDelete}
      />,
    )
    await user.click(screen.getByRole('button', { name: /^remove$/i }))
    await waitFor(() => expect(onDelete).toHaveBeenCalled())
  })
})
