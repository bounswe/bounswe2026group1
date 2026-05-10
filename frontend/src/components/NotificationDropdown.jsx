import { useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useNotifications, useMarkNotificationRead, useMarkAllNotificationsRead } from '../hooks/useNotifications.js'

const TYPE_ICON = {
  STATUS_CHANGE: 'task_alt',
  NEW_COMMENT: 'chat_bubble',
  NAV_ALERT: 'navigation',
  BADGE_AWARDED: 'workspace_premium',
}

function timeAgo(isoString) {
  const diff = Date.now() - new Date(isoString).getTime()
  const s = Math.floor(diff / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

function NotificationItem({ notification, onRead }) {
  const icon = TYPE_ICON[notification.type] ?? 'notifications'

  return (
    <button
      type="button"
      onClick={() => onRead(notification)}
      className={`w-full text-left flex items-start gap-3 px-4 py-3 hover:bg-surface-container transition-colors ${
        notification.read ? '' : 'bg-primary/5'
      }`}
    >
      <span
        className={`material-symbols-outlined mt-0.5 shrink-0 text-xl ${
          notification.read ? 'text-on-surface-variant' : 'text-primary'
        }`}
        style={{ fontVariationSettings: "'FILL' 1" }}
      >
        {icon}
      </span>
      <div className="flex-1 min-w-0">
        <p className={`text-sm leading-snug ${notification.read ? 'text-on-surface-variant' : 'text-on-surface font-semibold'}`}>
          {notification.message}
        </p>
        <p className="text-xs text-outline mt-0.5">{timeAgo(notification.createdAt)}</p>
      </div>
      {!notification.read && (
        <span className="w-2 h-2 rounded-full bg-primary shrink-0 mt-1.5" />
      )}
    </button>
  )
}

/**
 * Props:
 *  - onClose: () => void
 *  - triggerRef: ref to the button that opens this dropdown — clicks
 *      inside it are ignored by the outside-click handler so the trigger
 *      can toggle the dropdown closed without racing this listener.
 */
function NotificationDropdown({ onClose, triggerRef }) {
  const { t } = useTranslation()
  const { data: notifications, isLoading } = useNotifications()
  const { mutate: markRead } = useMarkNotificationRead()
  const { mutate: markAllRead, isPending: markingAll } = useMarkAllNotificationsRead()
  const navigate = useNavigate()
  const ref = useRef(null)

  useEffect(() => {
    function handlePointerDown(e) {
      if (ref.current?.contains(e.target)) return
      if (triggerRef?.current?.contains(e.target)) return
      onClose()
    }
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, triggerRef])

  function handleRead(notification) {
    if (!notification.read) markRead(notification.id)
    if (notification.relatedEntityId) {
      navigate(`/?report=${notification.relatedEntityId}`)
    }
    onClose()
  }

  const unread = notifications ? notifications.filter((n) => !n.read) : []
  const unreadCount = unread.length

  return (
    <div
      ref={ref}
      className="absolute right-0 mt-2 w-80 max-h-[480px] flex flex-col rounded-xl bg-surface-container-high shadow-lg ring-1 ring-outline-variant/30 z-[1100] overflow-hidden"
      role="dialog"
      aria-label={t('notifications.title')}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-outline-variant/30 shrink-0">
        <span className="font-semibold text-sm text-on-surface">{t('notifications.title')}</span>
        {unreadCount > 0 && (
          <button
            type="button"
            onClick={() => markAllRead(unread.map((n) => n.id))}
            disabled={markingAll}
            className="text-xs text-primary font-medium hover:underline disabled:opacity-50"
          >
            {t('notifications.markAllRead')}
          </button>
        )}
      </div>

      {/* Body */}
      <div className="overflow-y-auto flex-1">
        {isLoading ? (
          <div className="flex flex-col gap-2 p-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-12 rounded-lg bg-surface-container animate-pulse" />
            ))}
          </div>
        ) : !notifications || notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant gap-2">
            <span className="material-symbols-outlined text-3xl">notifications_none</span>
            <p className="text-sm">{t('notifications.empty')}</p>
          </div>
        ) : (
          <div className="divide-y divide-outline-variant/20">
            {notifications.map((n) => (
              <NotificationItem key={n.id} notification={n} onRead={handleRead} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default NotificationDropdown
