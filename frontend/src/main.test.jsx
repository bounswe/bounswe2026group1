import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('react-dom/client', () => ({
  createRoot: vi.fn(() => ({ render: vi.fn() })),
}))
vi.mock('./App.jsx', () => ({ default: () => null }))
vi.mock('./index.css', () => ({}))
vi.mock('leaflet/dist/leaflet.css', () => ({}))

import { createRoot } from 'react-dom/client'

describe('main.jsx bootstrap', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="root"></div>'
    vi.clearAllMocks()
    vi.resetModules()
  })

  it('creates a root and renders the provider tree', async () => {
    await import('./main.jsx')
    expect(createRoot).toHaveBeenCalledWith(document.getElementById('root'))
    const rootApi = createRoot.mock.results[0]?.value
    expect(rootApi.render).toHaveBeenCalled()
  })
})
