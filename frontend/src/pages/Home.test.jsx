import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext.jsx'
import Home from './Home.jsx'

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: () => null,
  Polyline: () => null,
  useMap: () => ({ flyTo: vi.fn(), zoomIn: vi.fn(), zoomOut: vi.fn(), locate: vi.fn(), on: vi.fn(), off: vi.fn() }),
  useMapEvents: () => null,
}))

vi.mock('../services/reportService.js', () => ({
  getReports: vi.fn().mockResolvedValue([]),
  mapReport: vi.fn((r) => r),
}))

const mockNavigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

function renderHome() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Home />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('Home page — location search bar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    global.fetch = vi.fn()
  })

  it('clears the input when focused', async () => {
    const user = userEvent.setup()
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    expect(input.value).toBe('')
  })

  it('shows suggestions after typing 2+ characters', async () => {
    const user = userEvent.setup()
    global.fetch.mockResolvedValue({
      json: async () => [
        { place_id: 1, display_name: 'Boğaziçi University, Istanbul, Turkey', lat: '41.0831', lon: '29.0500' },
        { place_id: 2, display_name: 'Boğaziçi, Düzce, Turkey', lat: '40.9', lon: '31.1' },
      ],
    })
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'bo')
    await waitFor(() => {
      expect(screen.getByText(/Boğaziçi University/)).toBeInTheDocument()
    })
  })

  it('does not fetch suggestions for less than 2 characters', async () => {
    const user = userEvent.setup()
    global.fetch.mockResolvedValue({ json: async () => [] })
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'b')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('shows error message when no results found on Enter', async () => {
    const user = userEvent.setup()
    global.fetch.mockResolvedValue({ json: async () => [] })
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'xyznonexistent{Enter}')
    await waitFor(() => {
      expect(screen.getByText(/no results found/i)).toBeInTheDocument()
    })
  })

  it('shows error message when fetch fails on Enter', async () => {
    const user = userEvent.setup()
    global.fetch.mockRejectedValue(new Error('Network error'))
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'istanbul{Enter}')
    await waitFor(() => {
      expect(screen.getByText(/search failed/i)).toBeInTheDocument()
    })
  })

  it('clears suggestions and blurs on Escape', async () => {
    const user = userEvent.setup()
    global.fetch.mockResolvedValue({
      json: async () => [
        { place_id: 1, display_name: 'Istanbul, Turkey', lat: '41.0', lon: '28.9' },
      ],
    })
    renderHome()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'ist')
    await waitFor(() => expect(screen.getByText('Istanbul, Turkey')).toBeInTheDocument())
    await user.keyboard('{Escape}')
    expect(screen.queryByText('Istanbul, Turkey')).not.toBeInTheDocument()
  })
})
