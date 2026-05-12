import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RoutePanel from './RoutePanel.jsx'

function mockDesktopViewport() {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  )
}

function mockMobileViewport() {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query) => ({
      matches: query.includes('1023px'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  )
}

function baseProps(overrides = {}) {
  return {
    routeOrigin: null,
    routeDest: null,
    routeOriginLabel: '',
    routeDestLabel: '',
    routes: null,
    activeRouteIndex: 0,
    routeError: '',
    loading: false,
    userLocation: null,
    onUseMyLocation: vi.fn(),
    onSwap: vi.fn(),
    onClearOrigin: vi.fn(),
    onClearDest: vi.fn(),
    onPickOrigin: vi.fn(),
    onPickDest: vi.fn(),
    onSelectRoute: vi.fn(),
    onReset: vi.fn(),
    ...overrides,
  }
}

describe('RoutePanel', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    mockDesktopViewport()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('calls onReset when close is activated', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onReset = vi.fn()
    render(<RoutePanel {...baseProps({ onReset })} />)

    await user.click(screen.getByRole('button', { name: /close route panel/i }))
    expect(onReset).toHaveBeenCalled()
  })

  it('shows loading state', () => {
    render(<RoutePanel {...baseProps({ loading: true })} />)
    expect(screen.getByText(/finding routes/i)).toBeInTheDocument()
  })

  it('shows route error message', () => {
    render(<RoutePanel {...baseProps({ routeError: 'Failed hard' })} />)
    expect(screen.getByText('Failed hard')).toBeInTheDocument()
  })

  it('renders route alternatives and selects active route', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onSelectRoute = vi.fn()
    const routes = [
      { label: 'Accessible Route', coords: [], distance: 800, duration: 600, hasObstacles: false },
      { label: 'Wheelchair Route', coords: [], distance: 1200, duration: 3600, hasObstacles: false },
      { label: 'Ramp shortcut', coords: [], distance: 100, duration: 120, hasObstacles: true },
    ]
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routes,
          activeRouteIndex: 0,
          onSelectRoute,
        })}
      />,
    )

    expect(screen.getByText('800 m')).toBeInTheDocument()
    expect(screen.getByText(/10 min/i)).toBeInTheDocument()
    expect(screen.getByText(/1h 0m/i)).toBeInTheDocument()
    expect(screen.getByText(/1\.20 km/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /wheelchair route/i }))
    expect(onSelectRoute).toHaveBeenCalledWith(1)
  })

  it('shows global obstacle warning when every route has obstacles', () => {
    const routes = [
      { label: 'Fast', coords: [], distance: 100, duration: 60, hasObstacles: true },
      { label: 'Slow', coords: [], distance: 200, duration: 120, hasObstacles: true },
    ]
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routes,
        })}
      />,
    )
    expect(screen.getByText(/no obstacle-free route could be found/i)).toBeInTheDocument()
  })

  it('disables use my location when geolocation is unavailable', () => {
    render(<RoutePanel {...baseProps()} />)
    expect(screen.getByRole('button', { name: /location not available/i })).toBeDisabled()
  })

  it('uses current location when offered', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onUseMyLocation = vi.fn()
    render(
      <RoutePanel
        {...baseProps({
          userLocation: { lat: 10, lng: 20 },
          onUseMyLocation,
        })}
      />,
    )
    await user.click(screen.getByRole('button', { name: /use my current location/i }))
    expect(onUseMyLocation).toHaveBeenCalled()
  })

  it('debounced origin search calls Nominatim and picks first suggestion', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onPickOrigin = vi.fn()
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [
        {
          place_id: 7,
          display_name: 'Galata, Istanbul, Turkey',
          lat: '41.03',
          lon: '28.97',
        },
      ],
    })

    render(<RoutePanel {...baseProps({ onPickOrigin })} />)

    await user.type(screen.getByPlaceholderText(/search or click map/i), 'Gal')
    await vi.advanceTimersByTimeAsync(450)

    await waitFor(() => expect(global.fetch).toHaveBeenCalled())
    const suggestion = await screen.findByText(/Galata, Istanbul, Turkey/)
    await user.click(suggestion.closest('button'))
    expect(onPickOrigin).toHaveBeenCalledWith(
      { lat: 41.03, lng: 28.97 },
      'Galata, Istanbul',
    )
  })

  it('calls onSwap when both endpoints exist', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onSwap = vi.fn()
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 1 },
          routeDest: { lat: 2, lng: 2 },
          onSwap,
        })}
      />,
    )
    await user.click(screen.getByRole('button', { name: /swap origin and destination/i }))
    expect(onSwap).toHaveBeenCalled()
  })

  it('clears origin via summary row click', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onClearOrigin = vi.fn()
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 40, lng: 29 },
          routeOriginLabel: 'My place',
          onClearOrigin,
        })}
      />,
    )
    await user.click(screen.getByText('My place'))
    expect(onClearOrigin).toHaveBeenCalled()
  })

  it('clears destination via summary row click', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onClearDest = vi.fn()
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routeDestLabel: 'Museum gate',
          onClearDest,
        })}
      />,
    )
    await user.click(screen.getByText('Museum gate'))
    expect(onClearDest).toHaveBeenCalled()
  })

  it('debounced destination search selects the second endpoint', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const onPickDest = vi.fn()
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [
        {
          place_id: 9,
          display_name: 'Kadıköy, Istanbul, Turkey',
          lat: '40.99',
          lon: '29.02',
        },
      ],
    })

    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 41, lng: 29 },
          onPickDest,
        })}
      />,
    )

    await user.type(screen.getByPlaceholderText(/search or click map/i), 'Kad')
    await vi.advanceTimersByTimeAsync(450)

    await waitFor(() => expect(global.fetch).toHaveBeenCalled())
    const suggestion = await screen.findByText(/Kadıköy, Istanbul, Turkey/)
    await user.click(suggestion.closest('button'))
    expect(onPickDest).toHaveBeenCalledWith(
      { lat: 40.99, lng: 29.02 },
      'Kadıköy, Istanbul',
    )
  })

  it('swallows suggestion failures without crashing', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockRejectedValue(new Error('network'))
    render(<RoutePanel {...baseProps()} />)

    await user.type(screen.getByPlaceholderText(/search or click map/i), 'Gal')
    await vi.advanceTimersByTimeAsync(450)

    await waitFor(() => expect(global.fetch).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Galata/i })).not.toBeInTheDocument()
  })

  it('uses neutral styling for generic route labels', () => {
    const routes = [
      { label: 'Plain route', coords: [], distance: 50, duration: 60, hasObstacles: false },
    ]
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routes,
          activeRouteIndex: 0,
        })}
      />,
    )
    expect(screen.getByText('Plain route')).toBeInTheDocument()
    expect(screen.getByText('50 m')).toBeInTheDocument()
  })

  it('formats long distances in kilometres', () => {
    const routes = [
      { label: 'Highway', coords: [], distance: 2500, duration: 3600, hasObstacles: false },
    ]
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routes,
        })}
      />,
    )
    expect(screen.getByText('2.50 km')).toBeInTheDocument()
  })

  it('renders the mobile resize handle on narrow viewports', () => {
    mockMobileViewport()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    )
    render(<RoutePanel {...baseProps()} />)
    expect(screen.getByRole('slider', { name: /resize route panel/i })).toBeInTheDocument()
  })

  it('dismisses the mobile sheet when the handle drag ends below the dismiss threshold', () => {
    mockMobileViewport()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    )
    const onReset = vi.fn()
    const prevInner = window.innerHeight
    Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 500 })
    render(<RoutePanel {...baseProps({ onReset })} />)

    const handle = screen.getByRole('slider', { name: /resize route panel/i })
    handle.setPointerCapture = vi.fn()
    handle.releasePointerCapture = vi.fn()
    act(() => {
      fireEvent.pointerDown(handle, { pointerId: 42, clientY: 40 })
      fireEvent.pointerMove(handle, { pointerId: 42, clientY: 420 })
      fireEvent.pointerUp(handle, { pointerId: 42, clientY: 420 })
    })

    expect(onReset).toHaveBeenCalled()
    Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: prevInner })
  })

  it('updates layout when matchMedia reports a desktop breakpoint after mount', () => {
    const state = { matches: true }
    const mql = {
      get matches() {
        return state.matches
      },
      media: '(max-width: 1023px)',
      addEventListener(_event, fn) {
        mql._listener = fn
      },
      removeEventListener: vi.fn(),
    }
    vi.stubGlobal('matchMedia', vi.fn(() => mql))
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    )

    render(<RoutePanel {...baseProps()} />)
    expect(screen.getByRole('slider', { name: /resize route panel/i })).toBeInTheDocument()

    act(() => {
      state.matches = false
      mql._listener()
    })

    expect(screen.queryByRole('slider', { name: /resize route panel/i })).not.toBeInTheDocument()
  })

  it('uses Accessible / Wheelchair / Ramp palette hints when labels match', () => {
    const routes = [
      { label: 'Accessible east side', coords: [], distance: 100, duration: 120, hasObstacles: false },
      { label: 'Wheelchair campus loop', coords: [], distance: 200, duration: 180, hasObstacles: false },
      { label: 'Ramp shortcut', coords: [], distance: 50, duration: 60, hasObstacles: true },
    ]
    render(
      <RoutePanel
        {...baseProps({
          routeOrigin: { lat: 1, lng: 2 },
          routeDest: { lat: 3, lng: 4 },
          routes,
          activeRouteIndex: 0,
        })}
      />,
    )
    expect(screen.getByText('Accessible east side')).toBeInTheDocument()
    expect(screen.getByText('Wheelchair campus loop')).toBeInTheDocument()
    expect(screen.getByText('Ramp shortcut')).toBeInTheDocument()
  })
})
