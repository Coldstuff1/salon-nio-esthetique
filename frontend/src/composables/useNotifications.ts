import { onMounted, onUnmounted, readonly, ref } from 'vue'
import { api } from '@/api/http'

/** How often the bell asks for the unread count while the tab is visible. */
const POLL_INTERVAL_MS = 60_000

const unread = ref(0)

/** Asks the backend for the signed-in person's unread count; a failed call keeps the last one. */
export async function refreshUnread() {
  try {
    const { data } = await api.GET('/api/me/notifications/unread-count')
    if (data) unread.value = data.count
  } catch {
    // offline: try again on the next tick
  }
}

/**
 * The unread notification count, shared by the header bell and the notifications page. The component that
 * calls this keeps it fresh while mounted: every minute while the tab is visible, and as soon as it's shown
 * again.
 */
export function useNotifications() {
  let timer: ReturnType<typeof setInterval> | undefined

  function onVisibilityChange() {
    if (document.visibilityState === 'visible') void refreshUnread()
  }

  onMounted(() => {
    void refreshUnread()
    timer = setInterval(() => {
      if (document.visibilityState === 'visible') void refreshUnread()
    }, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisibilityChange)
  })
  onUnmounted(() => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { unread: readonly(unread) }
}
