import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext.jsx'
import CreateReportPanel from './CreateReportPanel.jsx'
import * as api from '../services/api.js'

vi.mock('../services/api.js')
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: () => null,
  useMapEvents: (handlers) => { handlers; return null },
}))

const FAKE_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwiaWQiOjEsInJvbGUiOiJVU0VSIn0.sig'

function renderPanel({ token = FAKE_TOKEN, onClose = vi.fn(), onCreated = vi.fn() } = {}) {
  if (token) localStorage.setItem('token', token)
  return render(
    <MemoryRouter>
      <AuthProvider>
        <CreateReportPanel position={null} onClose={onClose} onCreated={onCreated} />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('CreateReportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ─── Rendering ───────────────────────────────────────────────────────────────

  describe('rendering', () => {
    it('renders the New Report heading', () => {
      renderPanel()
      expect(screen.getByText('New Report')).toBeInTheDocument()
    })

    it('renders all category buttons', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /missing ramp/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /broken elevator/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /narrow passage/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /wet floor/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /construction/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /ramp available/i })).toBeInTheDocument()
    })

    it('renders the description textarea', () => {
      renderPanel()
      expect(screen.getByPlaceholderText(/provide a brief description/i)).toBeInTheDocument()
    })

    it('renders Submit and Cancel buttons', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /submit report/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument()
    })

    it('shows "click on the map" hint when no position', () => {
      renderPanel()
      expect(screen.getByText(/click on the map to set location/i)).toBeInTheDocument()
    })
  })

  // ─── Validation ──────────────────────────────────────────────────────────────

  describe('form validation', () => {
    it('shows error when submitting without a location', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/click on the map to set a location/i)).toBeInTheDocument()
    })

    it('shows error when submitting without a category', async () => {
      const user = userEvent.setup()
      localStorage.setItem('token', FAKE_TOKEN)
      render(
        <MemoryRouter>
          <AuthProvider>
            <CreateReportPanel
              position={{ lat: 41.0, lng: 29.0 }}
              onClose={vi.fn()}
              onCreated={vi.fn()}
            />
          </AuthProvider>
        </MemoryRouter>
      )
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/select a category/i)).toBeInTheDocument()
    })

    it('shows error when submitting without a description', async () => {
      const user = userEvent.setup()
      localStorage.setItem('token', FAKE_TOKEN)
      render(
        <MemoryRouter>
          <AuthProvider>
            <CreateReportPanel
              position={{ lat: 41.0, lng: 29.0 }}
              onClose={vi.fn()}
              onCreated={vi.fn()}
            />
          </AuthProvider>
        </MemoryRouter>
      )
      await user.click(screen.getByRole('button', { name: /construction/i }))
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/provide a description/i)).toBeInTheDocument()
    })
  })

  // ─── Category selection ───────────────────────────────────────────────────────

  describe('category selection', () => {
    it('highlights selected category', async () => {
      const user = userEvent.setup()
      renderPanel()
      const btn = screen.getByRole('button', { name: /construction/i })
      await user.click(btn)
      expect(btn.className).toContain('border-primary')
    })
  })

  // ─── Close button ─────────────────────────────────────────────────────────────

  describe('close button', () => {
    it('calls onClose when Cancel is clicked', async () => {
      const onClose = vi.fn()
      const user = userEvent.setup()
      renderPanel({ onClose })
      await user.click(screen.getByRole('button', { name: /cancel/i }))
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('calls onClose when X button is clicked', async () => {
      const onClose = vi.fn()
      const user = userEvent.setup()
      renderPanel({ onClose })
      await user.click(screen.getByRole('button', { name: /close/i }))
      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })

  // ─── Submission ───────────────────────────────────────────────────────────────

  describe('successful submission', () => {
    it('calls onCreated and onClose after successful submit', async () => {
      api.apiFetch.mockResolvedValue({
        reportId: 99,
        userId: 1,
        latitude: 41.0,
        longitude: 29.0,
        description: 'test',
        tag: 'CONSTRUCTION',
        status: 'PENDING',
        agrees: 0,
        disagrees: 0,
        publishDate: '2026-04-04',
        mediaUrls: [],
      })

      const onClose = vi.fn()
      const onCreated = vi.fn()
      const user = userEvent.setup()

      localStorage.setItem('token', FAKE_TOKEN)
      render(
        <MemoryRouter>
          <AuthProvider>
            <CreateReportPanel
              position={{ lat: 41.0, lng: 29.0 }}
              onClose={onClose}
              onCreated={onCreated}
            />
          </AuthProvider>
        </MemoryRouter>
      )

      await user.click(screen.getByRole('button', { name: /construction/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test description')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => {
        expect(onCreated).toHaveBeenCalledTimes(1)
        expect(onClose).toHaveBeenCalledTimes(1)
      })
    })

    it('shows error when API call fails', async () => {
      api.apiFetch.mockRejectedValue(new Error('Server error'))
      const user = userEvent.setup()

      localStorage.setItem('token', FAKE_TOKEN)
      render(
        <MemoryRouter>
          <AuthProvider>
            <CreateReportPanel
              position={{ lat: 41.0, lng: 29.0 }}
              onClose={vi.fn()}
              onCreated={vi.fn()}
            />
          </AuthProvider>
        </MemoryRouter>
      )

      await user.click(screen.getByRole('button', { name: /construction/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => {
        expect(screen.getByText(/failed to submit report/i)).toBeInTheDocument()
      })
    })
  })
})
