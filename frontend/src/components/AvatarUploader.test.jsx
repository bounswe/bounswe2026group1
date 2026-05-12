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

  it('accepts a file via drag-and-drop', async () => {
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:drag-preview')
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
    const zone = screen.getByText(/jpeg or png/i).closest('[class*="border-dashed"]')
    const file = new File(['x'], 'd.jpg', { type: 'image/jpeg' })
    fireEvent.dragEnter(zone, { dataTransfer: { files: [] } })
    expect(screen.getByText(/drop image to preview/i)).toBeInTheDocument()
    fireEvent.drop(zone, { dataTransfer: { files: [file] } })
    await user.click(screen.getByRole('button', { name: /save avatar/i }))
    await waitFor(() => expect(onUpload).toHaveBeenCalledWith(file))
    createSpy.mockRestore()
  })

  it('clears the pending upload when Cancel is clicked', async () => {
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
    await user.upload(screen.getByTestId('avatar-file-input'), new File(['x'], 'p.jpg', { type: 'image/jpeg' }))
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))
    expect(screen.queryByRole('button', { name: /save avatar/i })).not.toBeInTheDocument()
  })

  it('shows Change avatar when an image already exists', () => {
    render(
      <AvatarUploader
        currentAvatarUrl="https://example.com/a.png"
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: /change avatar/i })).toBeInTheDocument()
  })

  it('falls back when the current avatar URL fails to load', async () => {
    render(
      <AvatarUploader
        currentAvatarUrl="https://example.com/broken.png"
        isUploading={false}
        isDeleting={false}
        onUpload={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const img = screen.getByRole('img', { name: /avatar preview/i })
    fireEvent.error(img)
    expect(screen.queryByRole('img', { name: /avatar preview/i })).not.toBeInTheDocument()
  })

  it('does not delete when confirmation is declined', async () => {
    window.confirm.mockReturnValueOnce(false)
    const onDelete = vi.fn()
    const user = userEvent.setup()
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
    expect(onDelete).not.toHaveBeenCalled()
  })

  it('surfaces delete errors', async () => {
    const onDelete = vi.fn().mockRejectedValue(new Error('cannot remove'))
    const user = userEvent.setup()
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
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot remove/i)
  })
})
