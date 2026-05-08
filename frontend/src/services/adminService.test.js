import { apiFetch } from './api.js'
import {
  banUser,
  changeReportStatus,
  changeUserRole,
  createAdminUser,
  deleteAdminComment,
  deleteAdminReport,
  deleteAdminUser,
  deleteAdminValidation,
  getAdminComments,
  getAdminReports,
  getAdminStats,
  getAdminUser,
  getAdminUsers,
  getAdminValidations,
  unbanUser,
} from './adminService.js'

vi.mock('./api.js', () => ({ apiFetch: vi.fn() }))

const TOKEN = 'admin-jwt'
const AUTH_HEADERS = { Authorization: `Bearer ${TOKEN}` }

describe('adminService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiFetch.mockResolvedValue({})
  })

  describe('stats', () => {
    it('GET /api/admin/stats with auth header', async () => {
      await getAdminStats(TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/stats', { headers: AUTH_HEADERS })
    })
  })

  describe('users', () => {
    it('builds a query string from filters and drops empty values', async () => {
      await getAdminUsers({ status: 'ACTIVE', role: '', page: 2, size: 10 }, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users?status=ACTIVE&page=2&size=10', {
        headers: AUTH_HEADERS,
      })
    })

    it('uses defaults when no filters are passed', async () => {
      await getAdminUsers({}, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users?page=0&size=20', {
        headers: AUTH_HEADERS,
      })
    })

    it('GET /api/admin/users/{id}', async () => {
      await getAdminUser(7, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users/7', { headers: AUTH_HEADERS })
    })

    it('POST /api/admin/users with payload', async () => {
      const payload = { email: 'a@b.com', password: 'Aa1!aaaa', name: 'Ada', role: 'USER' }
      await createAdminUser(payload, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users', {
        method: 'POST',
        headers: AUTH_HEADERS,
        body: JSON.stringify(payload),
      })
    })

    it('PATCH ban/unban', async () => {
      await banUser(3, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users/3/ban', {
        method: 'PATCH',
        headers: AUTH_HEADERS,
      })
      await unbanUser(3, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users/3/unban', {
        method: 'PATCH',
        headers: AUTH_HEADERS,
      })
    })

    it('PATCH role', async () => {
      await changeUserRole(5, 'ADMIN', TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users/5/role', {
        method: 'PATCH',
        headers: AUTH_HEADERS,
        body: JSON.stringify({ role: 'ADMIN' }),
      })
    })

    it('DELETE user', async () => {
      await deleteAdminUser(9, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/users/9', {
        method: 'DELETE',
        headers: AUTH_HEADERS,
      })
    })
  })

  describe('reports', () => {
    it('builds query string with all filters', async () => {
      await getAdminReports(
        {
          status: 'PENDING',
          categoryId: 4,
          environment: 'INDOOR',
          type: 'OBSTACLE',
          dateFrom: '2026-01-01T00:00:00Z',
          dateTo: '2026-02-01T00:00:00Z',
          page: 1,
          size: 5,
        },
        TOKEN,
      )
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/admin/reports?status=PENDING&categoryId=4&environment=INDOOR&type=OBSTACLE&dateFrom=2026-01-01T00%3A00%3A00Z&dateTo=2026-02-01T00%3A00%3A00Z&page=1&size=5',
        { headers: AUTH_HEADERS },
      )
    })

    it('PATCH status', async () => {
      await changeReportStatus(11, 'VERIFIED', TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/reports/11/status', {
        method: 'PATCH',
        headers: AUTH_HEADERS,
        body: JSON.stringify({ status: 'VERIFIED' }),
      })
    })

    it('DELETE report', async () => {
      await deleteAdminReport(11, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/reports/11', {
        method: 'DELETE',
        headers: AUTH_HEADERS,
      })
    })
  })

  describe('comments', () => {
    it('builds query string from filters', async () => {
      await getAdminComments({ reportId: 1, authorId: 2 }, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/admin/comments?reportId=1&authorId=2&page=0&size=20',
        { headers: AUTH_HEADERS },
      )
    })

    it('DELETE comment', async () => {
      await deleteAdminComment(8, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/comments/8', {
        method: 'DELETE',
        headers: AUTH_HEADERS,
      })
    })
  })

  describe('validations', () => {
    it('builds query string from filters', async () => {
      await getAdminValidations({ reportId: 1, userId: 2, voteType: 'AGREE' }, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/admin/validations?reportId=1&userId=2&voteType=AGREE&page=0&size=20',
        { headers: AUTH_HEADERS },
      )
    })

    it('DELETE validation', async () => {
      await deleteAdminValidation(13, TOKEN)
      expect(apiFetch).toHaveBeenCalledWith('/api/admin/validations/13', {
        method: 'DELETE',
        headers: AUTH_HEADERS,
      })
    })
  })
})
