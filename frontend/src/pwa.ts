import type { Router } from 'vue-router'
import { api } from '@/api/http'
import { keycloak } from '@/auth/keycloak'
import { refreshUnread } from '@/composables/useNotifications'
import type { WorkerMessage } from './swMessages'

/**
 * Registers the service worker (src/sw.ts), which shows push notifications, and acts on what it tells this tab:
 * refresh the bell when one arrives, open its page inside the app when one is clicked.
 */
export function registerServiceWorker(router: Router) {
  if (!('serviceWorker' in navigator)) return
  // in development vite-plugin-pwa serves the worker unbundled, as a module
  const [url, type]: [string, WorkerType] = import.meta.env.PROD
    ? ['/sw.js', 'classic']
    : ['/dev-sw.js?dev-sw', 'module']
  navigator.serviceWorker
    .register(url, { scope: '/', type })
    .catch((error) => console.error('Service worker registration failed', error))

  navigator.serviceWorker.addEventListener('message', (event: MessageEvent<WorkerMessage>) => {
    const message = event.data
    if (!keycloak.authenticated) return
    if (message.type === 'notification-received') {
      void refreshUnread()
    } else if (message.type === 'notification-click') {
      void open(router, message.id, message.link)
    }
  })
}

async function open(router: Router, id: string, link: string | null) {
  await api.POST('/api/me/notifications/{id}/read', { params: { path: { id } } }).catch(() => {})
  void refreshUnread()
  await router.push(link ?? { name: 'notifications' })
}
