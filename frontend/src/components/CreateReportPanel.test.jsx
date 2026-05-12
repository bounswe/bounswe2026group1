import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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

const FAKE_REPORT_RESPONSE = {
  reportId: 99,
  userId: 1,
  latitude: 41.0,
  longitude: 29.0,
  description: 'test',
  reportType: 'OBSTACLE',
  environment: 'OUTDOOR',
  objects: [{ objectType: 'RAMP', issues: ['TOO_STEEP'], measurements: '{}' }],
  status: 'PENDING',
  agrees: 0,
  disagrees: 0,
  publishDate: '2026-04-04',
  mediaUrls: [],
}

function renderPanel({ token = FAKE_TOKEN, onClose = vi.fn(), onCreated = vi.fn(), onError = vi.fn(), position = null } = {}) {
  if (token) localStorage.setItem('token', token)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <CreateReportPanel position={position} onClose={onClose} onCreated={onCreated} onError={onError} />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

function renderPanelWithPosition(props = {}) {
  return renderPanel({ position: { lat: 41.0, lng: 29.0 }, ...props })
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

    it('renders the Obstacle and Feature report type buttons', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /obstacle/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /feature/i })).toBeInTheDocument()
    })

    it('renders the Outdoor and Indoor environment buttons', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /outdoor/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /indoor/i })).toBeInTheDocument()
    })

    it('renders the Add Object button', () => {
      renderPanel()
      expect(screen.getByRole('button', { name: /add object/i })).toBeInTheDocument()
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

    it('shows error when submitting without any objects', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/add at least one object/i)).toBeInTheDocument()
    })

    it('shows error when submitting with an object but no type selected', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/select a type for every object/i)).toBeInTheDocument()
    })

    it('shows error when submitting without a description', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(screen.getByText(/provide a description/i)).toBeInTheDocument()
    })
  })

  // ─── Object card behaviour ────────────────────────────────────────────────────

  describe('object cards', () => {
    it('adds an object card when Add Object is clicked', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      expect(screen.getByText(/select a type…/i)).toBeInTheDocument()
    })

    it('shows object type buttons inside the card after adding', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      expect(screen.getByRole('button', { name: /^ramp$/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^elevator$/i })).toBeInTheDocument()
    })

    it('shows issues after selecting an object type (Obstacle mode)', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      expect(screen.getByRole('checkbox', { name: /too steep/i })).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: /missing handrail/i })).toBeInTheDocument()
    })

    it('does not show issues in Feature mode', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /feature/i }))
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      expect(screen.queryByRole('checkbox', { name: /too steep/i })).not.toBeInTheDocument()
    })

    it('disables duplicate object type buttons', async () => {
      const user = userEvent.setup()
      renderPanel()
      // Add first object and select RAMP
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      // Add second object — RAMP button should be disabled
      await user.click(screen.getByRole('button', { name: /add object/i }))
      const rampButtons = screen.getAllByRole('button', { name: /^ramp$/i })
      expect(rampButtons[1]).toBeDisabled()
    })

    it('removes an object card when delete button is clicked', async () => {
      const user = userEvent.setup()
      renderPanel()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      expect(screen.getByText(/select a type…/i)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /remove object/i }))
      expect(screen.queryByText(/select a type…/i)).not.toBeInTheDocument()
    })

    it('expands optional measurements and increments slope via the stepper', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.click(screen.getByRole('button', { name: /show measurements \(optional\)/i }))

      const slopeInput = screen.getAllByPlaceholderText('—')[0]
      await user.clear(slopeInput)
      await user.type(slopeInput, '8')

      const measurementRow = slopeInput.closest('.flex.items-center.rounded-lg')
      const steppers = within(measurementRow).getAllByRole('button')
      await user.click(steppers[0])

      expect(slopeInput).toHaveValue(8.1)
    })

    it('opens the measurement tutorial modal from the object card', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.click(screen.getByRole('button', { name: /show measurements \(optional\)/i }))
      await user.click(screen.getByRole('button', { name: /how to measure ramp/i }))
      expect(
        screen.getByRole('dialog', { name: /how to measure ramp/i }),
      ).toBeInTheDocument()
    })

    it('closes the measurement tutorial modal', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.click(screen.getByRole('button', { name: /show measurements \(optional\)/i }))
      await user.click(screen.getByRole('button', { name: /how to measure ramp/i }))
      await user.click(screen.getByRole('button', { name: /close tutorial/i }))
      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /how to measure ramp/i })).not.toBeInTheDocument(),
      )
    })

    it('decrements measurement value with the lower stepper', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.click(screen.getByRole('button', { name: /show measurements \(optional\)/i }))

      const slopeInput = screen.getAllByPlaceholderText('—')[0]
      await user.clear(slopeInput)
      await user.type(slopeInput, '1.2')

      const measurementRow = slopeInput.closest('.flex.items-center.rounded-lg')
      const steppers = within(measurementRow).getAllByRole('button')
      await user.click(steppers[1])

      expect(slopeInput).toHaveValue(1.1)
    })

    it('adds a preview when a JPEG is dropped on the media zone', () => {
      renderPanelWithPosition()
      const dropZone = screen.getByText(/upload or drag photos/i).closest('.cursor-pointer')
      fireEvent.drop(dropZone, {
        dataTransfer: { files: [new File(['x'], 'drop.jpg', { type: 'image/jpeg' })] },
      })
      expect(screen.getByAltText('Preview')).toBeInTheDocument()
    })

    it('collapses an expanded object card when the header row is clicked', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      expect(screen.getByRole('checkbox', { name: /too steep/i })).toBeInTheDocument()

      const card = screen.getByRole('checkbox', { name: /too steep/i }).closest('.rounded-2xl')
      const header = card.querySelector('.cursor-pointer.select-none')
      await user.click(header)

      expect(screen.queryByRole('checkbox', { name: /too steep/i })).not.toBeInTheDocument()
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
      api.apiFetch.mockResolvedValue(FAKE_REPORT_RESPONSE)

      const onClose = vi.fn()
      const onCreated = vi.fn()
      const user = userEvent.setup()

      renderPanelWithPosition({ onClose, onCreated })

      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test description')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => {
        expect(onCreated).toHaveBeenCalledTimes(1)
        expect(onClose).toHaveBeenCalledTimes(1)
      })
    })

    it('does not show an error alert after a successful create (regression: stale onCreated call outside else-block)', async () => {
      api.apiFetch.mockResolvedValue(FAKE_REPORT_RESPONSE)
      const onCreated = vi.fn()
      const user = userEvent.setup()

      renderPanelWithPosition({ onCreated })

      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      // Wait for the API call to complete
      await waitFor(() => expect(onCreated).toHaveBeenCalled())

      // No error message should appear — the bug caused a ReferenceError on
      // the stale `onCreated(mapped)` outside the else-block, which was caught
      // and displayed as an error toast even though the create succeeded.
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('shows error when API call fails', async () => {
      api.apiFetch.mockRejectedValue(new Error('Server error'))
      const user = userEvent.setup()

      renderPanelWithPosition()

      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => {
        expect(screen.getByText(/server error/i)).toBeInTheDocument()
      })
    })

    it('calls onError with the failure message so Home can raise a toast (#522)', async () => {
      api.apiFetch.mockRejectedValue(new Error('Server error'))
      const onError = vi.fn()
      const user = userEvent.setup()

      renderPanelWithPosition({ onError })

      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'test')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => {
        expect(onError).toHaveBeenCalledWith('Server error')
      })
    })

    it('submits a feature report with indoor environment', async () => {
      api.apiFetch.mockResolvedValue({ ...FAKE_REPORT_RESPONSE, reportType: 'FEATURE', environment: 'INDOOR' })
      const user = userEvent.setup()

      renderPanelWithPosition()

      await user.click(screen.getByRole('button', { name: /^feature$/i }))
      await user.click(screen.getByRole('button', { name: /^indoor$/i }))
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'Helpful ramp inside the mall')
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => expect(api.apiFetch).toHaveBeenCalled())
    })

    it('clears outdoor-only object types when switching to indoor environment', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^sidewalk$/i }))
      await user.click(screen.getByRole('checkbox', { name: /missing/i }))
      await user.click(screen.getByRole('button', { name: /^indoor$/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'switch env')
      await user.click(screen.getByRole('button', { name: /submit report/i }))
      expect(await screen.findByText(/select a type for every object/i)).toBeInTheDocument()
    })
  })

  describe('media validation and edit mode', () => {
    beforeEach(() => {
      vi.stubEnv('VITE_API_URL', 'http://localhost:8080')
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
    })

    afterEach(() => {
      vi.unstubAllEnvs()
    })

    function renderEditPanel(overrides = {}) {
      const onClose = overrides.onClose ?? vi.fn()
      const onUpdated = overrides.onUpdated ?? vi.fn()
      const editReport = {
        id: 501,
        description: 'Existing text',
        environment: 'OUTDOOR',
        reportType: 'OBSTACLE',
        objects: [{ objectType: 'RAMP', issues: ['TOO_STEEP'], measurements: {} }],
        mediaUrls: ['https://cdn.example.com/old.jpg'],
        mediaIds: [900],
        ...overrides.editReport,
      }
      localStorage.setItem('token', FAKE_TOKEN)
      const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      return render(
        <MemoryRouter>
          <QueryClientProvider client={qc}>
            <AuthProvider>
              <CreateReportPanel editReport={editReport} onClose={onClose} onUpdated={onUpdated} />
            </AuthProvider>
          </QueryClientProvider>
        </MemoryRouter>,
      )
    }

    it('rejects unsupported media types at the dropzone', async () => {
      renderPanelWithPosition()
      const input = document.querySelector('input[type="file"]')
      fireEvent.change(input, {
        target: { files: [new File(['x'], 'bad.webp', { type: 'image/webp' })] },
      })
      expect(await screen.findByText(/Unsupported file type/i)).toBeInTheDocument()
    })

    it('rejects oversize media files', async () => {
      renderPanelWithPosition()
      const input = document.querySelector('input[type="file"]')
      const big = new File([new Uint8Array(16 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' })
      fireEvent.change(input, { target: { files: [big] } })
      expect(await screen.findByRole('alert')).toHaveTextContent(/File must be 15 MB or smaller/)
    })

    it('removes a staged attachment before submit', async () => {
      const user = userEvent.setup()
      renderPanelWithPosition()
      const input = document.querySelector('input[type="file"]')
      await user.upload(input, new File(['z'], 'z.jpg', { type: 'image/jpeg' }))
      await user.click(screen.getByRole('button', { name: /^remove image$/i }))
      expect(screen.queryByRole('button', { name: /^remove image$/i })).not.toBeInTheDocument()
    })

    it('posts new media after a successful edit save', async () => {
      api.apiFetch.mockResolvedValueOnce({
        reportId: 501,
        description: 'ok',
        objects: [],
        mediaUrls: ['https://cdn.example.com/old.jpg'],
      })
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [{ url: 'https://cdn.example.com/new.jpg', id: 2 }],
      })
      const user = userEvent.setup()
      const onUpdated = vi.fn()
      renderEditPanel({ onUpdated })

      const input = document.querySelector('input[type="file"]')
      await user.upload(input, new File(['n'], 'n.jpg', { type: 'image/jpeg' }))
      await user.click(screen.getByRole('button', { name: /save changes/i }))

      await waitFor(() => expect(api.apiFetch).toHaveBeenCalled())
      await waitFor(() =>
        expect(global.fetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/reports/501/media',
          expect.objectContaining({ method: 'POST' }),
        ),
      )
      await waitFor(() => expect(onUpdated).toHaveBeenCalled())
    })

    it('includes mediaIdsToRemove when existing media is marked for removal', async () => {
      api.apiFetch.mockImplementation(async (_url, opts) => {
        if (opts?.method === 'PUT') {
          const body = JSON.parse(opts.body)
          expect(body.mediaIdsToRemove).toEqual([900])
          return { reportId: 501, description: 'x', objects: [], mediaUrls: [] }
        }
        return {}
      })
      const user = userEvent.setup()
      renderEditPanel()

      await user.click(screen.getByRole('button', { name: /^remove media$/i }))
      await user.click(screen.getByRole('button', { name: /save changes/i }))

      await waitFor(() => expect(api.apiFetch).toHaveBeenCalled())
    })

    it('uploads media after create when files are attached', async () => {
      api.apiFetch.mockResolvedValueOnce({ ...FAKE_REPORT_RESPONSE, reportId: 77 })
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [{ url: 'https://cdn.example.com/up.jpg', id: 1 }],
      })
      const onCreated = vi.fn()
      const user = userEvent.setup()

      renderPanelWithPosition({ onCreated })

      await user.click(screen.getByRole('button', { name: /add object/i }))
      await user.click(screen.getByRole('button', { name: /^ramp$/i }))
      await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
      await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'with media')

      const input = document.querySelector('input[type="file"]')
      await user.upload(input, new File(['p'], 'p.jpg', { type: 'image/jpeg' }))
      await user.click(screen.getByRole('button', { name: /submit report/i }))

      await waitFor(() => expect(onCreated).toHaveBeenCalled())
      await waitFor(() =>
        expect(global.fetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/reports/77/media',
          expect.objectContaining({ method: 'POST' }),
        ),
      )
    })
  })

})
