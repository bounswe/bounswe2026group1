import { render, screen, act, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CreateFixRequestPanel from './CreateFixRequestPanel.jsx'

const mockNavigate = vi.hoisted(() => vi.fn())
const authState = vi.hoisted(() => ({
  token: 'mock-token',
  isAuthenticated: true,
  userId: 'user1',
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => authState,
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
    authState.token = 'mock-token'
    authState.isAuthenticated = true
    authState.userId = 'user1'
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
      expect(submitFixRequest).toHaveBeenCalledWith(42, [file], 'looks great', 'mock-token')
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

  test('redirects to login when unauthenticated on submit', async () => {
    authState.isAuthenticated = false
    authState.token = null
    renderPanel()

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /Submit Fix Report/i }))
    })

    expect(mockNavigate).toHaveBeenCalledWith('/login')
    expect(onCloseMock).toHaveBeenCalled()
    expect(submitFixRequest).not.toHaveBeenCalled()
  })

  test('rejects non-image files with a clear message', async () => {
    renderPanel()
    const fileInput = document.querySelector('input[type="file"]')
    const bad = new File(['x'], 'x.gif', { type: 'image/gif' })
    await act(async () => {
      fireEvent.change(fileInput, { target: { files: [bad] } })
    })
    expect(await screen.findByRole('alert')).toHaveTextContent(/JPG or PNG/i)
  })

  test('accepts photos via drag-and-drop', async () => {
    renderPanel()
    const dropZone = screen.getByText(/Upload or drop photos of the resolved spot/).closest('.rounded-2xl')
    const file = new File(['bytes'], 'fix.jpg', { type: 'image/jpeg' })
    await act(async () => {
      fireEvent.drop(dropZone, { dataTransfer: { files: [file] } })
    })
    expect(screen.getByAltText(/preview 1/i)).toBeInTheDocument()
  })

  test('truncates when adding more files than the remaining slots', async () => {
    renderPanel()
    const input = document.querySelector('input[type="file"]')
    const batch = Array.from({ length: 6 }, (_, i) => new File([`${i}`], `p${i}.jpg`, { type: 'image/jpeg' }))
    await act(async () => {
      await user.upload(input, batch)
    })
    expect(await screen.findByRole('alert')).toHaveTextContent(/only the first 5/i)
  })

  test('removes a staged photo', async () => {
    renderPanel()
    const input = document.querySelector('input[type="file"]')
    await act(async () => {
      await user.upload(input, new File(['b'], 'fix.jpg', { type: 'image/jpeg' }))
    })
    await act(async () => {
      await user.click(screen.getByRole('button', { name: /remove photo 1/i }))
    })
    expect(screen.queryByAltText(/preview 1/i)).not.toBeInTheDocument()
  })

  test('shows a generic message when the server returns an unexpected error', async () => {
    submitFixRequest.mockRejectedValue(new Error('upstream'))
    renderPanel()
    const input = document.querySelector('input[type="file"]')
    await act(async () => {
      await user.upload(input, new File(['b'], 'fix.jpg', { type: 'image/jpeg' }))
    })
    await act(async () => {
      await user.click(screen.getByRole('button', { name: /Submit Fix Report/i }))
    })
    expect(await screen.findByRole('alert')).toHaveTextContent(/upstream/i)
  })
})
