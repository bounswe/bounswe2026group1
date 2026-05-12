import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getRoutingPreferences,
  updateRoutingPreferences,
  listCustomProfiles,
  createCustomProfile,
  updateCustomProfile,
  deleteCustomProfile,
  activateCustomProfile,
} from './routingPreferencesService.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))
import { apiFetch } from './api.js'

describe('routingPreferencesService', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listCustomProfiles GETs routing profiles', async () => {
    apiFetch.mockResolvedValue([])
    await listCustomProfiles('tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-profiles', {
      headers: { Authorization: 'Bearer tok' },
    })
  })

  it('getRoutingPreferences GETs with bearer', async () => {
    apiFetch.mockResolvedValue({})
    await getRoutingPreferences('tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-preferences', {
      headers: { Authorization: 'Bearer tok' },
    })
  })

  it('updateRoutingPreferences PUTs JSON body', async () => {
    apiFetch.mockResolvedValue({})
    const body = { preferredPreset: 'NONE' }
    await updateRoutingPreferences(body, 'tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-preferences', {
      method: 'PUT',
      headers: { Authorization: 'Bearer tok' },
      body: JSON.stringify(body),
    })
  })

  it('createCustomProfile POSTs', async () => {
    apiFetch.mockResolvedValue({})
    await createCustomProfile({ name: 'A', constraints: [] }, 'tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-profiles', {
      method: 'POST',
      headers: { Authorization: 'Bearer tok' },
      body: JSON.stringify({ name: 'A', constraints: [] }),
    })
  })

  it('updateCustomProfile PUTs by id', async () => {
    apiFetch.mockResolvedValue({})
    await updateCustomProfile(5, { name: 'B' }, 'tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-profiles/5', {
      method: 'PUT',
      headers: { Authorization: 'Bearer tok' },
      body: JSON.stringify({ name: 'B' }),
    })
  })

  it('deleteCustomProfile DELETEs', async () => {
    apiFetch.mockResolvedValue(null)
    await deleteCustomProfile(7, 'tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-profiles/7', {
      method: 'DELETE',
      headers: { Authorization: 'Bearer tok' },
    })
  })

  it('activateCustomProfile POSTs activate', async () => {
    apiFetch.mockResolvedValue({})
    await activateCustomProfile(9, 'tok')
    expect(apiFetch).toHaveBeenCalledWith('/api/users/me/routing-profiles/9/activate', {
      method: 'POST',
      headers: { Authorization: 'Bearer tok' },
    })
  })
})
