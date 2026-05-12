import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import FeedLocationPickerModal from './FeedLocationPickerModal.jsx'

vi.mock('leaflet', () => ({
  default: {
    divIcon: vi.fn(() => ({})),
  },
}))

vi.mock('react-leaflet', async () => {
  const React = await import('react')
  const Ctx = React.createContext(null)
  const mapApi = { invalidateSize: vi.fn(), setView: vi.fn(), getZoom: () => 14 }
  return {
    MapContainer: ({ children }) =>
      React.createElement(
        Ctx.Provider,
        { value: mapApi },
        React.createElement('div', { 'data-testid': 'mock-map' }, children)
      ),
    TileLayer: () => null,
    Marker: () => null,
    Circle: () => null,
    useMap: () => React.useContext(Ctx),
    useMapEvents: () => null,
  }
})

vi.mock('./MapSearchBar.jsx', () => ({
  default: ({ onLocationPicked }) => (
    <button type="button" onClick={() => onLocationPicked({ lat: 40.5, lon: 29.4 })}>
      mock-pick-search
    </button>
  ),
}))

describe('FeedLocationPickerModal', () => {
  const baseProps = () => ({
    open: true,
    onDismiss: vi.fn(),
    center: { lat: 41.0683, lng: 29.0505 },
    radiusKm: 2,
    tileUrl: 'https://tile.example/{z}/{x}/{y}.png',
    tileAttr: '&copy; test',
    onFeedCenterChange: vi.fn(),
    onLocateUnavailable: vi.fn(),
  })

  it('renders nothing when closed', () => {
    const props = baseProps()
    props.open = false
    const { container } = render(<FeedLocationPickerModal {...props} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('opens an accessible dialog when open', async () => {
    render(<FeedLocationPickerModal {...baseProps()} />)
    await waitFor(() =>
      expect(
        screen.getByRole('dialog', { name: /choose reference location for community feed/i })
      ).toBeInTheDocument()
    )
    expect(screen.getByTestId('mock-map')).toBeInTheDocument()
  })

  it('calls onDismiss from Cancel', async () => {
    const user = userEvent.setup()
    const onDismiss = vi.fn()
    render(<FeedLocationPickerModal {...baseProps()} onDismiss={onDismiss} />)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /^cancel$/i })).toBeInTheDocument()
    )
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('calls onDismiss from Done', async () => {
    const user = userEvent.setup()
    const onDismiss = vi.fn()
    render(<FeedLocationPickerModal {...baseProps()} onDismiss={onDismiss} />)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /^done$/i })).toBeInTheDocument()
    )
    await user.click(screen.getByRole('button', { name: /^done$/i }))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('calls onDismiss when Escape is pressed', async () => {
    const user = userEvent.setup()
    const onDismiss = vi.fn()
    render(<FeedLocationPickerModal {...baseProps()} onDismiss={onDismiss} />)
    await waitFor(() =>
      expect(
        screen.getByRole('dialog', { name: /choose reference location for community feed/i })
      ).toBeInTheDocument()
    )
    await user.keyboard('{Escape}')
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('notifies onFeedCenterChange when search picks a location', async () => {
    const user = userEvent.setup()
    const onFeedCenterChange = vi.fn()
    render(<FeedLocationPickerModal {...baseProps()} onFeedCenterChange={onFeedCenterChange} />)
    await waitFor(() => expect(screen.getByRole('button', { name: /mock-pick-search/i })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /mock-pick-search/i }))
    expect(onFeedCenterChange).toHaveBeenCalledWith({ lat: 40.5, lng: 29.4 })
  })

  it('calls onLocateUnavailable when geolocation is missing', async () => {
    const user = userEvent.setup()
    const onLocateUnavailable = vi.fn()
    const original = navigator.geolocation
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: undefined,
    })
    try {
      render(<FeedLocationPickerModal {...baseProps()} onLocateUnavailable={onLocateUnavailable} />)
      await waitFor(() =>
        expect(screen.getByRole('button', { name: /use my location/i })).toBeInTheDocument()
      )
      await user.click(screen.getByRole('button', { name: /use my location/i }))
      expect(onLocateUnavailable).toHaveBeenCalledTimes(1)
    } finally {
      Object.defineProperty(navigator, 'geolocation', {
        configurable: true,
        value: original,
      })
    }
  })
})
