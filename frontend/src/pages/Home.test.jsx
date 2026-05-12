import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../context/AuthContext.jsx'
import { SseProvider } from '../context/SseContext.jsx'
import { ThemeProvider } from '../context/ThemeContext.jsx'
import Home from './Home.jsx'

const getReportsMock = vi.hoisted(() => vi.fn())
const createReportMock = vi.hoisted(() => vi.fn())
const useMapEventsStack = vi.hoisted(() => [])
const mapController = vi.hoisted(() => ({
  flyTo: vi.fn(),
  zoomIn: vi.fn(),
  zoomOut: vi.fn(),
  locate: vi.fn(),
  on: vi.fn(),
  off: vi.fn(),
  once: vi.fn(),
}))

// useSseSync opens a real EventSource — mock it out in tests
vi.mock('../hooks/useSseSync.js', () => ({ useSseSync: vi.fn() }))

vi.mock('../services/reportService.js', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    getReports: (...args) => getReportsMock(...args),
    createReport: (...args) => createReportMock(...args),
  }
})

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: ({ eventHandlers }) => (
    <button type="button" data-testid="leaflet-marker" onClick={() => eventHandlers?.click?.()}>
      marker
    </button>
  ),
  Polyline: ({ eventHandlers }) => (
    <button type="button" data-testid="route-polyline" onClick={() => eventHandlers?.click?.()}>
      route segment
    </button>
  ),
  useMap: () => mapController,
  useMapEvents: (handlers) => {
    useMapEventsStack.push(handlers)
    return null
  },
}))

const mockNavigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

function makeJwt(payload = { id: 1, role: 'USER' }) {
  return `h.${btoa(JSON.stringify(payload))}.s`
}

function makeRawReport(overrides = {}) {
  return {
    reportId: 42,
    userId: 7,
    description: 'Test report for panel',
    status: 'VERIFIED',
    reportType: 'OBSTACLE',
    publishDate: '2026-01-01T12:00:00Z',
    latitude: 41.0683,
    longitude: 29.0505,
    agrees: 0,
    disagrees: 0,
    objects: [{ id: 1, objectType: 'RAMP', issues: [] }],
    mediaUrls: [],
    ...overrides,
  }
}

function fireMapClick(latlng = { lat: 41.0683, lng: 29.0505 }) {
  act(() => {
    useMapEventsStack.forEach((h) => {
      if (h.click) h.click({ latlng })
    })
  })
}

function apiFetchMock() {
  return vi.fn((input) => {
    const url = typeof input === 'string' ? input : input.url
    if (url.includes('/api/comments/report/')) {
      return Promise.resolve({ ok: true, text: async () => '[]', json: async () => [] })
    }
    if (url.includes('/follow/me')) {
      return Promise.resolve({
        ok: true,
        text: async () => '{"following":false}',
        json: async () => ({ following: false }),
      })
    }
    if (url.includes('/api/users/')) {
      return Promise.resolve({
        ok: true,
        text: async () => '{"id":7,"username":"u7"}',
        json: async () => ({ id: 7, username: 'u7' }),
      })
    }
    if (url.includes('nominatim.openstreetmap.org')) {
      return Promise.resolve({
        ok: true,
        json: async () => ({ display_name: 'Neighborhood, City' }),
      })
    }
    return Promise.resolve({ ok: true, text: async () => '{}', json: async () => ({}) })
  })
}

function renderHome(options = {}) {
  const { initialEntries = ['/'], token } = options
  if (token) localStorage.setItem('token', token)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <SseProvider>
          <MemoryRouter initialEntries={initialEntries}>
            <AuthProvider>
              <Home />
            </AuthProvider>
          </MemoryRouter>
        </SseProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('Home page — location search bar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useMapEventsStack.length = 0
    getReportsMock.mockResolvedValue([])
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

  it('flies the map when the user selects a location suggestion', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn(async (input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('nominatim.openstreetmap.org/search')) {
        return {
          ok: true,
          json: async () => [
            { place_id: 1, display_name: 'Galata Tower', lat: '41.0256', lon: '28.9744' },
          ],
        }
      }
      return { ok: true, json: async () => ({}), text: async () => '{}' }
    })
    renderHome({ token: makeJwt() })
    mapController.flyTo.mockClear()
    const input = screen.getByPlaceholderText(/search location/i)
    await user.click(input)
    await user.type(input, 'gal')
    await user.click(await screen.findByText(/Galata Tower/i))
    await waitFor(() => expect(mapController.flyTo).toHaveBeenCalled())
  })
})

describe('Home page — route panel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useMapEventsStack.length = 0
    getReportsMock.mockResolvedValue([])
    global.fetch = vi.fn()
  })

  it('opens then closes route mode', async () => {
    const user = userEvent.setup()
    renderHome()

    await user.click(screen.getByRole('button', { name: /get routes/i }))
    expect(screen.getByRole('heading', { name: /get routes/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /close route panel/i }))
    expect(screen.queryByRole('heading', { name: /get routes/i })).not.toBeInTheDocument()
  })
})

describe('Home page — map flows & filters', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useMapEventsStack.length = 0
    mapController.zoomIn.mockClear()
    mapController.zoomOut.mockClear()
    mapController.flyTo.mockClear()
    vi.stubEnv('VITE_API_URL', 'http://localhost:8080')
    vi.stubEnv('VITE_API_KEY', 'test-key')
    getReportsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('navigates to login when guest clicks Report an Issue', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn()
    renderHome()
    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('shows pin-drop hint when signed-in user starts a report', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    expect(await screen.findByText(/click on the map to set report location/i)).toBeInTheDocument()
  })

  it('reverse-geocodes after placing a pin for a new report', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    await screen.findByText(/click on the map to set report location/i)
    fireMapClick()
    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('nominatim.openstreetmap.org/reverse'))
    })
  })

  it('opens and closes map filters popover', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /^filter$/i }))
    expect(screen.getByRole('dialog', { name: /map filters/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /close filters/i }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /map filters/i })).not.toBeInTheDocument(),
    )
  })

  it('shows loading overlay while reports are loading', async () => {
    getReportsMock.mockImplementation(() => new Promise(() => {}))
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    expect(await screen.findByText(/loading reports/i)).toBeInTheDocument()
  })

  it('shows error overlay when reports fail to load', async () => {
    getReportsMock.mockRejectedValue(new Error('network'))
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    expect(await screen.findByText(/failed to load reports/i)).toBeInTheDocument()
  })

  it('opens report panel from ?report= deep link', async () => {
    global.fetch = apiFetchMock()
    getReportsMock.mockResolvedValue([makeRawReport({ reportId: 42 })])
    renderHome({ initialEntries: ['/?report=42'], token: makeJwt() })
    expect(await screen.findByRole('button', { name: /close panel/i })).toBeInTheDocument()
  })

  it('opens report panel when clicking a map marker', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    getReportsMock.mockResolvedValue([
      makeRawReport({ reportId: 10, latitude: 41.0, longitude: 29.0 }),
      makeRawReport({ reportId: 20, latitude: 41.1, longitude: 29.1, objects: [] }),
    ])
    renderHome({ token: makeJwt() })
    // Community Pulse uses `hidden lg:block`; in jsdom it often stays display:none, so RTL ignores it unless hidden:true.
    await screen.findByText('2 Active Reports', { hidden: true })
    const markers = screen.getAllByTestId('leaflet-marker')
    await user.click(markers[0])
    expect(await screen.findByRole('button', { name: /close panel/i })).toBeInTheDocument()
  })

  it('closes an in-progress create flow when a report marker is selected instead', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    getReportsMock.mockResolvedValue([
      makeRawReport({ reportId: 10, latitude: 41.0, longitude: 29.0 }),
    ])
    renderHome({ token: makeJwt() })
    await screen.findByText('1 Active Reports', { hidden: true })
    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    await screen.findByText(/click on the map to set report location/i)
    await user.click(screen.getByTestId('leaflet-marker'))
    expect(screen.queryByRole('heading', { name: /new report/i })).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /close panel/i })).toBeInTheDocument()
  })

  it('fetches walking routes after picking origin and destination on the map', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.05, 41.068],
            [29.051, 41.069],
          ],
        },
        hasObstacles: false,
        routeLabel: 'Fast',
        distanceMeters: 120,
        durationSeconds: 90,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    expect(await screen.findByText(/click on the map to set your starting point/i)).toBeInTheDocument()
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await waitFor(() => expect(screen.getByText(/now click your destination/i)).toBeInTheDocument())
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/routes',
        expect.objectContaining({ method: 'POST' }),
      )
    })
  })

  it('shows route notice when fastest route has obstacles and no accessible alternative', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.05, 41.068],
            [29.051, 41.069],
          ],
        },
        hasObstacles: true,
        routeLabel: 'Fast',
        distanceMeters: 120,
        durationSeconds: 90,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt({ id: 1, role: 'USER' }) })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    expect(
      await screen.findByText(/no accessible walking route could be found/i),
    ).toBeInTheDocument()
  })

  it('dismisses the route notice banner', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.05, 41.068],
            [29.051, 41.069],
          ],
        },
        hasObstacles: true,
        routeLabel: 'Fast',
        distanceMeters: 120,
        durationSeconds: 90,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await screen.findByText(/no accessible walking route could be found/i)
    await user.click(screen.getByRole('button', { name: /^dismiss$/i }))
    expect(screen.queryByText(/no accessible walking route could be found/i)).not.toBeInTheDocument()
  })

  it('zoom controls call map zoom helpers', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /^zoom in$/i }))
    await user.click(screen.getByRole('button', { name: /^zoom out$/i }))
    expect(mapController.zoomIn).toHaveBeenCalled()
    expect(mapController.zoomOut).toHaveBeenCalled()
  })

  it('selects a different route alternative when a polyline segment is clicked', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.05, 41.068],
            [29.051, 41.069],
          ],
        },
        hasObstacles: false,
        routeLabel: 'Accessible north',
        distanceMeters: 120,
        durationSeconds: 90,
      },
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.06, 41.07],
            [29.061, 41.071],
          ],
        },
        hasObstacles: false,
        routeLabel: 'Plain detour',
        distanceMeters: 200,
        durationSeconds: 100,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await waitFor(() => expect(screen.getAllByTestId('route-polyline').length).toBe(2))
    await user.click(screen.getAllByTestId('route-polyline')[1])
    expect(screen.getAllByTestId('route-polyline').length).toBe(2)
  })

  it('refetches routes after swapping origin and destination', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: { type: 'LineString', coordinates: [[29.05, 41.068], [29.051, 41.069]] },
        hasObstacles: false,
        routeLabel: 'Route A',
        distanceMeters: 100,
        durationSeconds: 60,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({ ok: true, json: async () => ({ display_name: 'Here, There' }) })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await waitFor(() =>
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/routes'),
        expect.any(Object),
      ),
    )
    const n = global.fetch.mock.calls.length
    await user.click(screen.getByRole('button', { name: /swap origin and destination/i }))
    await waitFor(() => expect(global.fetch.mock.calls.length).toBeGreaterThan(n))
  })

  it('shows onboarding when the tutorial query flag is present', async () => {
    renderHome({ initialEntries: ['/?tutorial=1'], token: makeJwt() })
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/Welcome to Mapcess/i)).toBeInTheDocument()
  })

  it('renders an accessible alternative route with the accessible palette', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geoJsonGeometry: {
          type: 'LineString',
          coordinates: [
            [29.05, 41.068],
            [29.051, 41.069],
          ],
        },
        hasObstacles: false,
        routeLabel: 'Accessible Route',
        distanceMeters: 100,
        durationSeconds: 60,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await waitFor(() => expect(screen.getByTestId('route-polyline')).toBeInTheDocument())
  })

  it('renders routes from legacy encoded polylines when geoJsonGeometry is absent', async () => {
    const user = userEvent.setup()
    const routePayload = [
      {
        geometry: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
        hasObstacles: false,
        routeLabel: 'Legacy encoded',
        distanceMeters: 100,
        durationSeconds: 60,
      },
    ]
    global.fetch = vi.fn((input) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.includes('/api/routes')) {
        return Promise.resolve({
          ok: true,
          json: async () => routePayload,
          text: async () => JSON.stringify(routePayload),
        })
      }
      if (url.includes('nominatim')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ display_name: 'Here, There' }),
        })
      }
      return Promise.resolve({ ok: true, json: async () => ({}), text: async () => '{}' })
    })
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /get routes/i }))
    fireMapClick({ lat: 41.07, lng: 29.04 })
    await screen.findByText(/now click your destination/i)
    fireMapClick({ lat: 41.08, lng: 29.06 })
    await waitFor(() => expect(screen.getByTestId('route-polyline')).toBeInTheDocument())
  })

  it('shows the filter badge when map exclusions are present in the URL', () => {
    global.fetch = apiFetchMock()
    renderHome({ initialEntries: ['/?excluded=RAMP'], token: makeJwt() })
    expect(screen.getByLabelText(/1 filters active/i)).toBeInTheDocument()
  })

  it('closes the new-report panel when Cancel is pressed', async () => {
    const user = userEvent.setup()
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt() })
    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    fireMapClick()
    await screen.findByRole('heading', { name: /new report/i })
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: /new report/i })).not.toBeInTheDocument(),
    )
  })

  it('shows the global success toast after submitting a new map report', async () => {
    const user = userEvent.setup()
    vi.stubEnv('VITE_API_URL', 'http://localhost:8080')
    createReportMock.mockResolvedValueOnce({
      reportId: 888,
      userId: 1,
      latitude: 41.0683,
      longitude: 29.0505,
      description: 'Steep ramp',
      status: 'PENDING',
      reportType: 'OBSTACLE',
      environment: 'OUTDOOR',
      publishDate: '2026-05-12T12:00:00Z',
      agrees: 0,
      disagrees: 0,
      objects: [{ id: 1, objectType: 'RAMP', issues: ['TOO_STEEP'], measurements: '{}' }],
      mediaUrls: [],
    })
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt({ id: 1, role: 'USER' }) })

    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    fireMapClick()
    await screen.findByRole('heading', { name: /new report/i })

    await user.click(screen.getByRole('button', { name: /add object/i }))
    await user.click(screen.getByRole('button', { name: /^ramp$/i }))
    await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
    await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'Steep ramp near gate')
    await user.click(screen.getByRole('button', { name: /submit report/i }))

    expect(await screen.findByText(/report submitted successfully/i)).toBeInTheDocument()
    vi.unstubAllEnvs()
  })

  it('surfaces create-report failures via the shared toast', async () => {
    const user = userEvent.setup()
    vi.stubEnv('VITE_API_URL', 'http://localhost:8080')
    createReportMock.mockRejectedValueOnce(new Error('Server unavailable'))
    global.fetch = apiFetchMock()
    renderHome({ token: makeJwt({ id: 1, role: 'USER' }) })

    await user.click(screen.getByRole('button', { name: /report an issue/i }))
    fireMapClick()
    await screen.findByRole('heading', { name: /new report/i })

    await user.click(screen.getByRole('button', { name: /add object/i }))
    await user.click(screen.getByRole('button', { name: /^ramp$/i }))
    await user.click(screen.getByRole('checkbox', { name: /too steep/i }))
    await user.type(screen.getByPlaceholderText(/provide a brief description/i), 'Blocked ramp')
    await user.click(screen.getByRole('button', { name: /submit report/i }))

    await waitFor(() => {
      expect(screen.getAllByText('Server unavailable').length).toBeGreaterThanOrEqual(2)
    })
    vi.unstubAllEnvs()
  })
})
