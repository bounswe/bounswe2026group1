import { render, screen, waitFor, within, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '../context/AuthContext.jsx'
import { ThemeProvider } from '../context/ThemeContext.jsx'
import Feed from './Feed.jsx'

const { getReportFeedMock, mapReportMock, userSearchState } = vi.hoisted(() => ({
  getReportFeedMock: vi.fn(),
  mapReportMock: vi.fn(),
  userSearchState: {
    data: { content: [] },
    isFetching: false,
  },
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

vi.mock('../hooks/useUserSearch.js', () => ({
  useUserSearch: vi.fn(() => ({
    data: userSearchState.data,
    isFetching: userSearchState.isFetching,
  })),
  USER_SEARCH_MIN_LENGTH: 2,
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
    title: 'Feed test report title',
    description: 'Ramp too steep — details for the card body.',
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

/** Minimal JWT so `AuthProvider` treats the user as signed in (author filter requires auth). */
function makeJwt(payload = { id: 1, role: 'USER' }) {
  return `h.${btoa(JSON.stringify(payload))}.s`
}

function mapReportForTest(r) {
  return {
    id: r.reportId,
    // Distinct title vs description so queries don't match two nodes (card h2 + body p).
    title: r.title ?? 'Feed test report title',
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

/** Captures the IntersectionObserver callback so tests can trigger infinite-scroll loads. */
let feedIntersectionCallback = () => {}
beforeAll(() => {
  global.IntersectionObserver = class IntersectionObserverMock {
    constructor(cb) {
      feedIntersectionCallback = cb
    }
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
    userSearchState.data = { content: [] }
    userSearchState.isFetching = false
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
      expect(
        screen.getByRole('heading', { name: 'Feed test report title' })
      ).toBeInTheDocument()
    })
    expect(mapReportMock).toHaveBeenCalled()
  })

  it('requests the first page without coordinates when no reference point is set', async () => {
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())
    const [, tokenArg, optsArg] = getReportFeedMock.mock.calls[0]
    expect(tokenArg === undefined || tokenArg === null).toBe(true)
    expect(optsArg).toMatchObject({ signal: expect.any(AbortSignal) })
    expect(getReportFeedMock.mock.calls[0][0]).toMatchObject({
      page: 0,
      size: 20,
      reportType: 'ALL',
      environment: 'ALL',
      latitude: undefined,
      longitude: undefined,
      radiusInKm: undefined,
    })
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
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Feed test report title' })
      ).toBeInTheDocument()
    )

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
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Feed test report title' })
      ).toBeInTheDocument()
    )

    await user.click(screen.getByRole('button', { name: /environment: indoor/i }))

    await waitFor(() => {
      expect(screen.getByText(/no reports match your filters/i)).toBeInTheDocument()
    })
    expect(
      screen.queryByRole('heading', { name: 'Feed test report title' })
    ).not.toBeInTheDocument()
  })

  it('surfaces API errors', async () => {
    getReportFeedMock.mockRejectedValueOnce(new Error('Service unavailable'))
    renderFeed()
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/service unavailable/i)
    })
  })

  it('passes selected status filters as an array to getReportFeed', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /status: validated/i }))

    await waitFor(() => {
      const withStatus = getReportFeedMock.mock.calls.filter((call) =>
        Array.isArray(call[0]?.status) && call[0].status.includes('VERIFIED')
      )
      expect(withStatus.length).toBeGreaterThan(0)
    })
  })

  it('passes the selected sort option to getReportFeed', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'MOST_AGREED')

    await waitFor(() => {
      const withSort = getReportFeedMock.mock.calls.filter(
        (call) => call[0]?.sort === 'MOST_AGREED'
      )
      expect(withSort.length).toBeGreaterThan(0)
    })
  })

  it('passes the debounced description search to getReportFeed', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.type(screen.getByLabelText(/search description/i), 'elevator')

    await waitFor(
      () => {
        const withQ = getReportFeedMock.mock.calls.filter((call) => call[0]?.q === 'elevator')
        expect(withQ.length).toBeGreaterThan(0)
      },
      { timeout: 1500 }
    )
  })

  it('blocks the feed request and shows a warning when Nearest is picked without a location', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())
    getReportFeedMock.mockClear()

    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'DISTANCE')

    await waitFor(() => {
      expect(
        screen.getByText(/pick a reference point to sort by distance/i)
      ).toBeInTheDocument()
    })
    // No new feed request should have fired while the sort needs data we don't have.
    expect(getReportFeedMock).not.toHaveBeenCalled()
  })

  it('reveals advanced filters when toggled and clears all on demand', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    // Advanced panel is hidden initially.
    expect(screen.queryByLabelText(/published after/i)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /advanced filters/i }))
    expect(screen.getByLabelText(/published after/i)).toBeInTheDocument()

    // Activate a filter so "Clear all" shows up, then reset.
    await user.click(screen.getByRole('button', { name: /status: pending/i }))
    const clearBtn = await screen.findByRole('button', { name: /clear all/i })
    await user.click(clearBtn)

    await waitFor(() => {
      const lastCall = getReportFeedMock.mock.calls.at(-1)
      expect(lastCall?.[0]?.status).toBeUndefined()
    })
  })

  it('opens the map picker from the distance-sort warning banner', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'DISTANCE')
    const banner = await screen.findByText(/pick a reference point to sort by distance/i)
    const box = banner.closest('[role="status"]')
    await user.click(within(box).getByRole('button', { name: /choose on map/i }))
    expect(screen.getByTestId('feed-location-modal')).toBeInTheDocument()
  })

  it('passes min agrees and min disagrees from advanced filters', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /advanced filters/i }))
    await user.type(screen.getByLabelText(/^min agrees$/i), '3')
    await user.type(screen.getByLabelText(/^min disagrees$/i), '1')

    await waitFor(() => {
      const last = getReportFeedMock.mock.calls.at(-1)?.[0]
      expect(last).toMatchObject({ minAgrees: 3, minDisagrees: 1 })
    })
  })

  it('sets reference coords from the banner “Use current location” action', async () => {
    const user = userEvent.setup()
    global.navigator.geolocation = {
      getCurrentPosition: vi.fn((success) => {
        success({ coords: { latitude: 41.015, longitude: 29.985 } })
      }),
    }
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'DISTANCE')
    const banner = await screen.findByText(/pick a reference point to sort by distance/i)
    const box = banner.closest('[role="status"]')
    await user.click(within(box).getByRole('button', { name: /use current location/i }))

    expect(await screen.findByText(/reference point:/i)).toHaveTextContent('41.0150')
    expect(screen.getByText(/reference point:/i)).toHaveTextContent('29.9850')
  })

  it('fetches the next page when the infinite-scroll sentinel intersects', async () => {
    getReportFeedMock
      .mockResolvedValueOnce(
        makeFeedPage({
          content: [makeApiReport({ reportId: 501 })],
          last: false,
          number: 0,
          totalPages: 3,
        }),
      )
      .mockResolvedValue(
        makeFeedPage({
          content: [makeApiReport({ reportId: 502, title: 'Page two card' })],
          last: true,
          number: 1,
          totalPages: 3,
        }),
      )

    renderFeed()
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Feed test report title' })).toBeInTheDocument(),
    )

    await act(async () => {
      feedIntersectionCallback([{ isIntersecting: true }])
    })

    await waitFor(() => {
      expect(getReportFeedMock.mock.calls.some((c) => c[0]?.page === 1)).toBe(true)
    })
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Page two card' })).toBeInTheDocument()
    })
  })

  it('passes object type and issue filters from advanced filters', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /advanced filters/i }))
    await user.click(screen.getByRole('button', { name: /object type: ramp/i }))
    await user.click(screen.getByRole('button', { name: /ramp issue: too steep/i }))

    await waitFor(() => {
      const last = getReportFeedMock.mock.calls.at(-1)?.[0]
      expect(last?.objectType).toEqual(expect.arrayContaining(['RAMP']))
      expect(last?.issueType).toEqual(expect.arrayContaining(['TOO_STEEP']))
    })
  })

  it('filters by author id after picking a search result', async () => {
    localStorage.setItem('token', makeJwt())
    userSearchState.data = { content: [{ id: 77, name: 'Zoe Mapper' }] }

    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /advanced filters/i }))
    const authorInput = screen.getByPlaceholderText(/type at least 2 letters/i)
    await user.type(authorInput, 'zo')
    const opt = await screen.findByRole('option', { name: 'Zoe Mapper' })
    await user.click(opt)

    await waitFor(() => {
      expect(getReportFeedMock.mock.calls.some((c) => c[0]?.authorId === 77)).toBe(true)
    })
  })

  it('passes publishedBefore when set in advanced filters', async () => {
    const user = userEvent.setup()
    renderFeed()
    await waitFor(() => expect(getReportFeedMock).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /advanced filters/i }))
    const beforeInput = screen.getByLabelText(/^published before$/i)
    await user.type(beforeInput, '2026-06-01T12:00')

    await waitFor(() => {
      const last = getReportFeedMock.mock.calls.at(-1)?.[0]
      expect(last?.publishedBefore).toMatch(/^2026-06-01/)
    })
  })

  it('shows Loading more while the next page request is in flight', async () => {
    let resolveNext
    const pending = new Promise((res) => {
      resolveNext = res
    })
    getReportFeedMock
      .mockResolvedValueOnce(
        makeFeedPage({
          content: [makeApiReport({ reportId: 901 })],
          last: false,
          number: 0,
          totalPages: 2,
        }),
      )
      .mockImplementationOnce(() => pending)

    renderFeed()
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Feed test report title' })).toBeInTheDocument(),
    )

    await act(async () => {
      feedIntersectionCallback([{ isIntersecting: true }])
    })

    expect(await screen.findByText(/loading more/i)).toBeInTheDocument()

    await act(async () => {
      resolveNext(
        makeFeedPage({
          content: [makeApiReport({ reportId: 902 })],
          last: true,
          number: 1,
          totalPages: 2,
        }),
      )
    })
  })
})
