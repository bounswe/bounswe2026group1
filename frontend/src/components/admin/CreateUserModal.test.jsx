import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import CreateUserModal from './CreateUserModal.jsx'
import { useCreateAdminUser } from '../../hooks/useAdmin.js'

vi.mock('../../hooks/useAdmin.js', () => ({
  useCreateAdminUser: vi.fn(),
}))

function renderModal(props = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = props.onClose ?? vi.fn()
  return {
    onClose,
    ...render(
      <QueryClientProvider client={client}>
        <CreateUserModal open {...props} onClose={onClose} />
      </QueryClientProvider>,
    ),
  }
}

describe('CreateUserModal', () => {
  let mutate

  beforeEach(() => {
    vi.clearAllMocks()
    mutate = vi.fn()
    useCreateAdminUser.mockReturnValue({
      mutate,
      isPending: false,
    })
  })

  it('renders nothing when open is false', () => {
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <CreateUserModal open={false} onClose={() => {}} />
      </QueryClientProvider>,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders form fields and password hint', () => {
    renderModal()
    expect(screen.getByRole('heading', { name: /create user/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/^name$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^email$/i)).toBeInTheDocument()
    // Hint lives inside the same <label>, so the accessible name includes it.
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /role/i })).toBeInTheDocument()
    expect(screen.getByText(/min 8 chars/i)).toBeInTheDocument()
  })

  it('submits valid data and calls mutate with form payload', async () => {
    const user = userEvent.setup()
    mutate.mockImplementation((_payload, opts) => opts.onSuccess())
    const { onClose } = renderModal()

    await user.type(screen.getByLabelText(/^name$/i), 'Jane Doe')
    await user.type(screen.getByLabelText(/^email$/i), 'jane@example.com')
    await user.type(screen.getByLabelText(/password/i), 'Secret12!')
    await user.selectOptions(screen.getByRole('combobox', { name: /role/i }), 'ADMIN')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        {
          name: 'Jane Doe',
          email: 'jane@example.com',
          password: 'Secret12!',
          role: 'ADMIN',
        },
        expect.any(Object),
      )
    })
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('shows error message when mutation fails', async () => {
    const user = userEvent.setup()
    mutate.mockImplementation((_payload, opts) =>
      opts.onError({ message: 'Email already registered' }),
    )
    renderModal()

    await user.type(screen.getByLabelText(/^name$/i), 'Jane Doe')
    await user.type(screen.getByLabelText(/^email$/i), 'taken@example.com')
    await user.type(screen.getByLabelText(/password/i), 'Secret12!')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(await screen.findByText(/email already registered/i)).toBeInTheDocument()
  })

  it('calls onClose when Cancel is clicked', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal()
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalled()
  })

  it('does not call mutate when required fields are empty', async () => {
    const user = userEvent.setup()
    renderModal()
    await user.click(screen.getByRole('button', { name: /^create$/i }))
    expect(mutate).not.toHaveBeenCalled()
  })

  it('shows Creating… and disables actions while pending', () => {
    useCreateAdminUser.mockReturnValue({
      mutate,
      isPending: true,
    })
    renderModal()
    expect(screen.getByRole('button', { name: /creating/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
  })
})
