/**
 * The site's service worker. It caches nothing: it only shows the push notifications the backend sends
 * (backend PushSender) and opens the page one is about when it's clicked. Open tabs are told about both, so
 * the bell refreshes and a click navigates inside the app instead of reloading it (src/pwa.ts).
 */
import type { WorkerMessage } from './swMessages'

declare const self: ServiceWorkerGlobalScope

/** What the backend's PushSender sends, already in the recipient's language. */
interface PushPayload {
  id: string
  title: string
  body: string
  link: string | null
}

// a new version takes over at once: nothing it serves depends on the version of the open tabs
self.addEventListener('install', () => void self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

async function tabs() {
  return self.clients.matchAll({ type: 'window', includeUncontrolled: true })
}

async function tell(message: WorkerMessage) {
  for (const tab of await tabs()) tab.postMessage(message)
}

self.addEventListener('push', (event) => {
  const payload = event.data?.json() as PushPayload | undefined
  if (!payload) return
  event.waitUntil(
    Promise.all([
      self.registration.showNotification(payload.title, {
        body: payload.body,
        // the same notification pushed twice replaces itself
        tag: payload.id,
        icon: '/pwa-192x192.png',
        data: { id: payload.id, link: payload.link },
      }),
      tell({ type: 'notification-received' }),
    ]),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const { id, link } = event.notification.data as { id: string; link: string | null }
  event.waitUntil(
    (async () => {
      const tab = (await tabs())[0]
      if (tab) {
        await tab.focus()
        tab.postMessage({ type: 'notification-click', id, link } satisfies WorkerMessage)
      } else {
        await self.clients.openWindow(link ?? '/notifications')
      }
    })(),
  )
})
