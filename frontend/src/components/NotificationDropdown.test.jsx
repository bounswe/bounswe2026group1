import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import NotificationDropdown from './NotificationDropdown.jsx'

vi.mock('../hooks/useNotifications.js', () => ({
  useNotifications: vi.fn(),
  useMarkNotificationRead: vi.fn(),
  useMarkAllNotificationsRead: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
} from '../hooks/useNotifications.js'

const markReadMutate = vi.fn()
const markAllReadMutate = vi.fn()

function setupHooks(notifications, { isLoading = false } = {}) {
  useNotifications.mockReturnValue({ data: notifications, isLoading })
  useMarkNotificationRead.mockReturnValue({ mutate: markReadMutate })
  useMarkAllNotificationsRead.mockReturnValue({ mutate: markAllReadMutate, isPending: false })
}

function renderDropdown(onClose = vi.fn()) {
  return render(
    <MemoryRouter>
      <NotificationDropdown onClose={onClose} />
    </MemoryRouter>,
  )
}

const NOW = new Date().toISOString()

function makeNotif(overrides) {
  return { id: 1, type: 'STATUS_CHANGE', message: 'Test notification', relatedEntityId: 7, read: false, createdAt: NOW, ...overrides }
}

describe('NotificationDropdown', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  describe('loading and empty states', () => {
    it('shows loading skeletons while fetching', () => {
      setupHooks(undefined, { isLoading: true })
      renderDropdown()
      expect(document.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
    })

    it('shows empty state when there are no notifications', () => {
      setupHooks([])
      renderDropdown()
      expect(screen.getByText(/no notifications yet/i)).toBeInTheDocument()
    })
  })

  describe('"Mark all read" visibility', () => {
    it('is visible when there are unread notifications', () => {
      setupHooks([makeNotif({ read: false })])
      renderDropdown()
      expect(screen.getByRole('button', { name: /mark all read/i })).toBeInTheDocument()
    })

    it('is hidden when all notifications are already read', () => {
      setupHooks([makeNotif({ read: true })])
      renderDropdown()
      expect(screen.queryByRole('button', { name: /mark all read/i })).not.toBeInTheDocument()
    })
  })

  describe('clicking a notification', () => {
    it('calls markRead and navigates to the report when notification is unread', async () => {
      const user = userEvent.setup()
      setupHooks([makeNotif({ id: 5, relatedEntityId: 99, read: false })])
      renderDropdown()

      await user.click(screen.getByText('Test notification'))

      expect(markReadMutate).toHaveBeenCalledWith(5)
      expect(mockNavigate).toHaveBeenCalledWith('/?report=99')
    })

    it('does not call markRead but still navigates when notification is already read', async () => {
      const user = userEvent.setup()
      setupHooks([makeNotif({ id: 5, relatedEntityId: 99, read: true })])
      renderDropdown()

      await user.click(screen.getByText('Test notification'))

      expect(markReadMutate).not.toHaveBeenCalled()
      expect(mockNavigate).toHaveBeenCalledWith('/?report=99')
    })

    it('does not navigate when notification has no relatedEntityId', async () => {
      const user = userEvent.setup()
      setupHooks([makeNotif({ relatedEntityId: null, read: false })])
      renderDropdown()

      await user.click(screen.getByText('Test notification'))

      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })

  describe('"Mark all read" button', () => {
    it('passes only unread IDs to the mutation', async () => {
      const user = userEvent.setup()
      setupHooks([
        makeNotif({ id: 1, read: false, message: 'Unread A' }),
        makeNotif({ id: 2, read: true,  message: 'Already read' }),
        makeNotif({ id: 3, read: false, message: 'Unread B' }),
      ])
      renderDropdown()

      await user.click(screen.getByRole('button', { name: /mark all read/i }))

      expect(markAllReadMutate).toHaveBeenCalledWith([1, 3])
    })
  })

  describe('badge notifications', () => {
    it('renders the workspace_premium icon for BADGE_AWARDED notifications', () => {
      setupHooks([
        makeNotif({
          id: 9,
          type: 'BADGE_AWARDED',
          message: 'You earned the Trusted Reporter badge!',
          relatedEntityId: null,
        }),
      ])
      renderDropdown()
      // Icon ligature is the text content of the material-symbols span.
      expect(screen.getByText('workspace_premium')).toBeInTheDocument()
    })

    it('marks badge notification as read on click without navigating (no relatedEntityId)', async () => {
      const user = userEvent.setup()
      setupHooks([
        makeNotif({
          id: 9,
          type: 'BADGE_AWARDED',
          message: 'You earned the Trusted Reporter badge!',
          relatedEntityId: null,
          read: false,
        }),
      ])
      renderDropdown()

      await user.click(screen.getByText(/trusted reporter/i))

      expect(markReadMutate).toHaveBeenCalledWith(9)
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })

  describe('dismiss behaviour', () => {
    it('calls onClose when Escape is pressed', async () => {
      const user = userEvent.setup()
      const onClose = vi.fn()
      setupHooks([])
      renderDropdown(onClose)

      await user.keyboard('{Escape}')

      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('calls onClose when clicking outside the dropdown', () => {
      const onClose = vi.fn()
      setupHooks([])
      render(
        <MemoryRouter>
          <NotificationDropdown onClose={onClose} />
          <div data-testid="outside">outside</div>
        </MemoryRouter>,
      )

      fireEvent.pointerDown(screen.getByTestId('outside'))

      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })
})
