import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../context/AuthContext.jsx'
import { ThemeProvider } from '../context/ThemeContext.jsx'
import Feed from './Feed.jsx'

const { getReportFeedMock, mapReportMock } = vi.hoisted(() => ({
  getReportFeedMock: vi.fn(),
  mapReportMock: vi.fn(),
}))

vi.mock('../components/Navbar.jsx', () => ({
  default: () => <div data-testid="navbar" />,
}))

vi.mock('../components/FeedLocationPickerModal.jsx', () => ({
  default: ({ open, onDismiss, onFeedCenterChange }) =>
    open ? (
      <div data-testid="feed-location-modal">
        <button
          type="button"
          onClick={() => onFeedCenterChange({ lat: 41.08, lng: 29.05 })}
        >
          Confirm test location
        </button>
        <button type="button" onClick={onDismiss}>
          Close modal
        </button>
      </div>
    ) : null,
}))

vi.mock('../services/reportService.js', () => ({
  getReportFeed: (...args) => getReportFeedMock(...args),
  mapReport: (r) => mapReportMock(r),
}))

/** Minimal Spring `Page` JSON shape used by `useInfiniteQuery` + `getNextPageParam`. */
function makeFeedPage(overrides = {}) {
  return {
    content: [],
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    ...overrides,
  }
}

/** Raw API row before `mapReport` (subset of backend `ReportResponse`). */
function makeApiReport(overrides = {}) {
  return {
    reportId: 101,
    userId: 2,
    description: 'Ramp too steep',
    status: 'PENDING',
    reportType: 'OBSTACLE',
    environment: 'OUTDOOR',
    agrees: 3,
    disagrees: 0,
    publishDate: '2026-04-01T12:00:00Z',
    latitude: 41.086,
    longitude: 29.044,
    mediaUrls: [],
    objects: [],
    ...overrides,
  }
}

function mapReportForTest(r) {
  return {
    id: r.reportId,
    title: r.description ?? 'Report',
    description: r.description ?? '',
    status: 'unverified',
    date: '1 April 2026',
    location: '41.0860, 29.0440',
    agrees: r.agrees ?? 0,
    disagrees: r.disagrees ?? 0,
    reportType: r.reportType ?? 'OBSTACLE',
    environment: r.environment ?? 'OUTDOOR',
    image: null,
    latitude: r.latitude,
    longitude: r.longitude,
    objects: [],
    mediaUrls: [],
    userVote: null,
    activeFixRequest: null,
    fixedAt: null,
  }
}

beforeAll(() => {
  global.IntersectionObserver = class IntersectionObserverMock {
    constructor() {}
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return []
    }
  }
})

function renderFeed() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MemoryRouter>
          <AuthProvider>
            <Feed />
          </AuthProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

describe('Feed page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mapReportMock.mockImplementation(mapReportForTest)
    getReportFeedMock.mockResolvedValue(
      makeFeedPage({
        content: [makeApiReport()],
        totalElements: 1,
        last: true,
      })
    )
  })

  it('renders the community feed heading', () => {
    renderFeed()
    expect(
      screen.getByRole('heading', { name: /community feed/i })
    ).toBeInTheDocument()
  })

  it('loads reports and shows mapped content', async () => {
    renderFeed()
    await waitFor(() => {
      expect(screen.getByText('Ramp too steep')).toBeInTheDocument()
    })
    expect(mapReportMock).toHaveBeenCalled()
  })

  it('requests the first page without coordinates when no reference point is set', async () => {
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())
    expect(getReportFeedMock).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
        reportType: 'ALL',
        environment: 'ALL',
        latitude: undefined,
        longitude: undefined,
        radiusInKm: undefined,
      }),
      undefined,
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    )
  })

  it('requests obstacle-only reports after selecting the Obstacle filter', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    const obstacleBtn = screen.getByRole('button', { name: /report type: obstacle/i })
    await user.click(obstacleBtn)

    await waitFor(() => {
      const obstacleCalls = getReportFeedMock.mock.calls.filter((call) =>
        call[0]?.reportType === 'OBSTACLE'
      )
      expect(obstacleCalls.length).toBeGreaterThan(0)
    })
  })

  it('includes latitude, longitude, and radius when a map reference point is chosen', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(screen.getByText('Ramp too steep')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /choose on map/i }))
    expect(screen.getByTestId('feed-location-modal')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /confirm test location/i }))

    await waitFor(() => {
      const withGeo = getReportFeedMock.mock.calls.filter(
        (call) =>
          call[0]?.latitude === 41.08 &&
          call[0]?.longitude === 29.05 &&
          call[0]?.radiusInKm === 5
      )
      expect(withGeo.length).toBeGreaterThan(0)
    })
  })

  it('shows an empty-state message when filters exclude every report', async () => {
    getReportFeedMock.mockResolvedValue(
      makeFeedPage({
        content: [makeApiReport({ reportType: 'OBSTACLE', environment: 'OUTDOOR' })],
        totalElements: 1,
        last: true,
      })
    )
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(screen.getByText('Ramp too steep')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /environment: indoor/i }))

    await waitFor(() => {
      expect(screen.getByText(/no reports match your filters/i)).toBeInTheDocument()
    })
    expect(screen.queryByText('Ramp too steep')).not.toBeInTheDocument()
  })

  it('surfaces API errors', async () => {
    getReportFeedMock.mockRejectedValueOnce(new Error('Service unavailable'))
    renderFeed()
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/service unavailable/i)
    })
  })
})
