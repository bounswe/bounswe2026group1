import { render, screen, act, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CreateFixRequestPanel from './CreateFixRequestPanel.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => ({ token: 'mock-token', isAuthenticated: true, userId: 'user1' }),
}))

vi.mock('../services/reportService.js', () => ({
  submitFixRequest: vi.fn(),
}))

import { submitFixRequest } from '../services/reportService.js'

describe('CreateFixRequestPanel', () => {
  let onCloseMock, onSubmittedMock, user

  beforeEach(() => {
    onCloseMock = vi.fn()
    onSubmittedMock = vi.fn()
    user = userEvent.setup()
    vi.clearAllMocks()
    // jsdom doesn't provide URL.createObjectURL by default
    if (!URL.createObjectURL) URL.createObjectURL = vi.fn(() => 'blob:preview')
  })

  function renderPanel(props = {}) {
    return render(
      <MemoryRouter>
        <CreateFixRequestPanel
          reportId={42}
          reportTitle="Missing Ramp"
          onClose={onCloseMock}
          onSubmitted={onSubmittedMock}
          {...props}
        />
      </MemoryRouter>
    )
  }

  test('shows the parent report title in the subhead', () => {
    renderPanel()
    expect(screen.getByText(/Missing Ramp/i)).toBeInTheDocument()
  })

  test('rejects submit when no photo is attached', async () => {
    renderPanel()

    const submitBtn = screen.getByRole('button', { name: /Submit Fix Report/i })
    await act(async () => { await user.click(submitBtn) })

    expect(submitFixRequest).not.toHaveBeenCalled()
    expect(await screen.findByRole('alert')).toHaveTextContent(/photo/i)
  })

  test('submits multipart and calls onSubmitted then onClose on success', async () => {
    submitFixRequest.mockResolvedValue({ id: 7, state: 'OPEN' })
    renderPanel()

    const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })
    const fileInput = document.querySelector('input[type="file"]')
    await act(async () => { await user.upload(fileInput, file) })

    const desc = screen.getByPlaceholderText(/A new ramp was installed/i)
    await act(async () => { await user.type(desc, 'looks great') })

    const submitBtn = screen.getByRole('button', { name: /Submit Fix Report/i })
    await act(async () => { await user.click(submitBtn) })

    await waitFor(() => {
      expect(submitFixRequest).toHaveBeenCalledWith(42, file, 'looks great', 'mock-token')
      expect(onSubmittedMock).toHaveBeenCalledWith({ id: 7, state: 'OPEN' })
      expect(onCloseMock).toHaveBeenCalled()
    })
  })

  test('shows a friendly conflict message on 409', async () => {
    const conflict = Object.assign(new Error('open exists'), { status: 409 })
    submitFixRequest.mockRejectedValue(conflict)
    renderPanel()

    const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })
    const fileInput = document.querySelector('input[type="file"]')
    await act(async () => { await user.upload(fileInput, file) })

    const submitBtn = screen.getByRole('button', { name: /Submit Fix Report/i })
    await act(async () => { await user.click(submitBtn) })

    expect(await screen.findByRole('alert')).toHaveTextContent(/already submitted/i)
    expect(onCloseMock).not.toHaveBeenCalled()
  })

  test('shows the backend message on 400 (e.g. oversized description)', async () => {
    const tooLong = Object.assign(new Error('Description must be at most 1000 characters'), { status: 400 })
    submitFixRequest.mockRejectedValue(tooLong)
    renderPanel()

    const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })
    const fileInput = document.querySelector('input[type="file"]')
    await act(async () => { await user.upload(fileInput, file) })

    const submitBtn = screen.getByRole('button', { name: /Submit Fix Report/i })
    await act(async () => { await user.click(submitBtn) })

    expect(await screen.findByRole('alert')).toHaveTextContent(/1000 characters/i)
  })
})
