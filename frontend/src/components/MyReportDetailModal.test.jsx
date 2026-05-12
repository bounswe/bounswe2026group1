import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import MyReportDetailModal from './MyReportDetailModal.jsx'

const mutateAsync = vi.hoisted(() => vi.fn())
vi.mock('../hooks/useUserReports.js', () => ({
  useDeleteReport: () => ({ mutateAsync, isPending: false }),
}))

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="route-loc">{location.pathname + location.search}</div>
}

function renderModal(report, userId = 1) {
  const qc = new QueryClient()
  const onClose = vi.fn()
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route
            path="/profile"
            element={
              <>
                <MyReportDetailModal report={report} userId={userId} onClose={onClose} />
                <LocationProbe />
              </>
            }
          />
          <Route path="/" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const BASE_REPORT = {
  id: 42,
  title: 'Gap',
  description: 'Test',
  status: 'unverified',
  environment: 'OUTDOOR',
  date: 'May 2026',
  location: '1, 2',
  objects: [],
  mediaUrls: [],
}

describe('MyReportDetailModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutateAsync.mockResolvedValue(undefined)
  })

  it('navigates to map for edit', async () => {
    const user = userEvent.setup()
    renderModal(BASE_REPORT)

    await user.click(screen.getByRole('button', { name: /edit on map/i }))
    expect(screen.getByTestId('route-loc')).toHaveTextContent('/?report=42')
  })

  it('deletes after confirm', async () => {
    const user = userEvent.setup()
    window.confirm = vi.fn(() => true)

    renderModal(BASE_REPORT)
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(window.confirm).toHaveBeenCalled()
    expect(mutateAsync).toHaveBeenCalledWith(42)
  })

  it('shows delete error when mutation fails', async () => {
    const user = userEvent.setup()
    window.confirm = vi.fn(() => true)
    mutateAsync.mockRejectedValueOnce(new Error('Forbidden'))

    renderModal(BASE_REPORT)
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Forbidden')
  })
})
