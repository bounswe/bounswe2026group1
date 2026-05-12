import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DangerZoneSection from './DangerZoneSection.jsx'

const mutateAsync = vi.hoisted(() => vi.fn())
vi.mock('../hooks/useProfile.js', () => ({
  useDeleteAccount: () => ({
    mutateAsync: mutateAsync,
    isPending: false,
  }),
}))

function renderWithRouter(ui) {
  const queryClient = new QueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route path="/profile" element={ui} />
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DangerZoneSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutateAsync.mockResolvedValue(undefined)
  })

  it('opens confirm dialog and requires exact email to enable delete', async () => {
    const user = userEvent.setup()
    renderWithRouter(<DangerZoneSection email="user@example.com" />)

    await user.click(screen.getByRole('button', { name: /delete my account/i }))
    expect(screen.getByRole('dialog', { name: /confirm account deletion/i })).toBeInTheDocument()

    const emailInput = screen.getByLabelText(/confirm email/i)
    await user.type(emailInput, 'wrong@example.com')
    expect(screen.getByRole('button', { name: /^delete account$/i })).toBeDisabled()

    await user.clear(emailInput)
    await user.type(emailInput, 'user@example.com')
    expect(screen.getByRole('button', { name: /^delete account$/i })).toBeEnabled()
  })

  it('submits delete and navigates home on success', async () => {
    const user = userEvent.setup()
    renderWithRouter(<DangerZoneSection email="a@b.co" />)

    await user.click(screen.getByRole('button', { name: /delete my account/i }))
    await user.type(screen.getByLabelText(/confirm email/i), 'a@b.co')
    await user.click(screen.getByRole('button', { name: /^delete account$/i }))

    expect(mutateAsync).toHaveBeenCalled()
  })

  it('shows server error when delete fails', async () => {
    const user = userEvent.setup()
    mutateAsync.mockRejectedValueOnce(new Error('Not allowed'))
    renderWithRouter(<DangerZoneSection email="a@b.co" />)

    await user.click(screen.getByRole('button', { name: /delete my account/i }))
    await user.type(screen.getByLabelText(/confirm email/i), 'a@b.co')
    await user.click(screen.getByRole('button', { name: /^delete account$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Not allowed')
  })
})
