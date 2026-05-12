import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MapSearchBar from './MapSearchBar.jsx'

describe('MapSearchBar', () => {
  const onLocationPicked = vi.fn()

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    )
    onLocationPicked.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('does not search until input has at least 2 characters', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(
      <MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} inputId="search-test" />,
    )

    await user.type(screen.getByPlaceholderText(/search location/i), 'x')
    await vi.advanceTimersByTimeAsync(500)
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('fetches suggestions after debounce when query is long enough', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [
        {
          place_id: 1,
          display_name: 'Istanbul, TR',
          lat: '41.0',
          lon: '29.0',
        },
      ],
    })

    render(
      <MapSearchBar
        mapCenter={{ lat: 41, lng: 29 }}
        onLocationPicked={onLocationPicked}
        inputId="search-test"
      />,
    )

    await user.type(screen.getByPlaceholderText(/search location/i), 'Is')
    await vi.advanceTimersByTimeAsync(450)

    expect(global.fetch).toHaveBeenCalled()
    expect(await screen.findByText(/Istanbul/)).toBeInTheDocument()
  })

  it('selects a suggestion and calls onLocationPicked with lon field', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [
        {
          place_id: 99,
          display_name: 'Ankara',
          lat: '39.9',
          lon: '32.8',
        },
      ],
    })

    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    await user.type(screen.getByPlaceholderText(/search location/i), 'Ank')
    await vi.advanceTimersByTimeAsync(450)

    await user.click(await screen.findByRole('button', { name: /Ankara/i }))
    expect(onLocationPicked).toHaveBeenCalledWith({ lat: 39.9, lon: 32.8 })
  })

  it('resolves lat,lng from keyboard Enter when typed directly', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    const input = screen.getByPlaceholderText(/search location/i)
    await user.clear(input)
    await user.type(input, '40.9, 29.1{Enter}')

    expect(onLocationPicked).toHaveBeenCalledWith({ lat: 40.9, lon: 29.1 })
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('on Enter uses first Nominatim result when not lat,lng', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [{ lat: '1', lon: '2', display_name: 'Somewhere' }],
    })

    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    const input = screen.getByPlaceholderText(/search location/i)
    await user.clear(input)
    await user.type(input, 'Somewhere unique{Enter}')
    await vi.runAllTimersAsync()

    expect(onLocationPicked).toHaveBeenCalledWith({ lat: 1, lon: 2 })
  })

  it('shows error when Nominatim returns no results on Enter', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockResolvedValue({ ok: true, json: async () => [] })

    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    const input = screen.getByPlaceholderText(/search location/i)
    await user.clear(input)
    await user.type(input, 'nothing{Enter}')
    await vi.runAllTimersAsync()

    expect(screen.getByText(/no results found/i)).toBeInTheDocument()
    expect(onLocationPicked).not.toHaveBeenCalled()
  })

  it('shows error when fetch throws on Enter search', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockRejectedValue(new Error('network'))

    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    const input = screen.getByPlaceholderText(/search location/i)
    await user.clear(input)
    await user.type(input, 'query{Enter}')
    await vi.runAllTimersAsync()

    expect(screen.getByText(/search failed/i)).toBeInTheDocument()
  })

  it('clears suggestions on Escape', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => [
        { place_id: 1, display_name: 'X', lat: '0', lon: '0' },
      ],
    })

    render(<MapSearchBar mapCenter={null} onLocationPicked={onLocationPicked} />)

    await user.type(screen.getByPlaceholderText(/search location/i), 'xx')
    await vi.advanceTimersByTimeAsync(450)
    expect(await screen.findByText('X')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByText('X')).not.toBeInTheDocument()
  })
})
