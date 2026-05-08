import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getNotifications, markNotificationRead, mintSseToken } from './notificationService.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))
import { apiFetch } from './api.js'

describe('notificationService', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getNotifications calls /api/notifications with Bearer token', async () => {
    apiFetch.mockResolvedValue([])
    await getNotifications('tok123')
    expect(apiFetch).toHaveBeenCalledWith('/api/notifications', {
      headers: { Authorization: 'Bearer tok123' },
    })
  })

  it('markNotificationRead sends PATCH to /api/notifications/{id}/read', async () => {
    apiFetch.mockResolvedValue({})
    await markNotificationRead(42, 'tok123')
    expect(apiFetch).toHaveBeenCalledWith('/api/notifications/42/read', {
      method: 'PATCH',
      headers: { Authorization: 'Bearer tok123' },
    })
  })

  // Critical: mintSseToken must include credentials so the browser stores the
  // HttpOnly sse_token cookie on the cross-origin response.
  it('mintSseToken POSTs to /api/sse/token with Bearer token and credentials: include', async () => {
    apiFetch.mockResolvedValue({})
    await mintSseToken('tok123')
    expect(apiFetch).toHaveBeenCalledWith('/api/sse/token', {
      method: 'POST',
      headers: { Authorization: 'Bearer tok123' },
      credentials: 'include',
    })
  })
})
