import { readonly, ref } from 'vue'
import { api } from '@/api/http'
import { beforeLogout } from '@/auth/keycloak'

/**
 * Whether this browser shows the signed-in person's notifications:
 * - `install-first`: iPhone or iPad, where only a site added to the Home Screen gets push
 * - `blocked`: the person refused notifications for the site; only the browser's settings can undo that
 */
export type PushState = 'unsupported' | 'install-first' | 'blocked' | 'off' | 'on'

/** Unknown until first read. */
const state = ref<PushState>()

const isAppleMobile = () =>
  /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
const isInstalled = () =>
  matchMedia('(display-mode: standalone)').matches ||
  (navigator as Navigator & { standalone?: boolean }).standalone === true
const isSupported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

async function currentSubscription() {
  const registration = await navigator.serviceWorker.getRegistration()
  return (await registration?.pushManager.getSubscription()) ?? null
}

function base64UrlToBytes(value: string) {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4))
  return Uint8Array.from(binary, (c) => c.charCodeAt(0))
}

function sameBytes(a: ArrayBuffer | null, b: Uint8Array) {
  return !!a && a.byteLength === b.length && new Uint8Array(a).every((byte, i) => byte === b[i])
}

/** The salon's VAPID key, which the browser subscribes with. */
async function serverKey() {
  const { data, error } = await api.GET('/api/public/push-key')
  if (!data) throw error
  return base64UrlToBytes(data.publicKey)
}

/** Tells the backend to push to this browser, for whoever is signed in. */
async function register(subscription: PushSubscription) {
  const { endpoint, keys } = subscription.toJSON()
  const { error } = await api.PUT('/api/me/push-subscriptions', {
    body: { endpoint: endpoint ?? '', keys: { p256dh: keys?.p256dh ?? '', auth: keys?.auth ?? '' } },
  })
  if (error) throw error
}

/** Reads where things stand in this browser, without asking anything. */
export async function readPushState() {
  if (!isSupported()) {
    state.value = isAppleMobile() && !isInstalled() ? 'install-first' : 'unsupported'
  } else if (Notification.permission === 'denied') {
    state.value = 'blocked'
  } else {
    state.value = Notification.permission === 'granted' && (await currentSubscription()) ? 'on' : 'off'
  }
  return state.value
}

/**
 * Once per visit, for signed-in people: if this browser has push on, makes sure the backend sends it this
 * person's notifications (someone else may have turned it on before, or the backend may have dropped it).
 * A subscription made with a key the salon has since replaced gets nothing, so it's made again.
 */
export async function syncPushSubscription() {
  if ((await readPushState()) !== 'on') return
  try {
    let subscription = (await currentSubscription())!
    const key = await serverKey()
    if (!sameBytes(subscription.options.applicationServerKey, key)) {
      await subscription.unsubscribe()
      const registration = await navigator.serviceWorker.ready
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: key,
      })
    }
    await register(subscription)
  } catch (e) {
    console.warn('Could not refresh the push subscription', e)
  }
}

/** Asks permission (the browser's own prompt), subscribes and registers this browser. */
export async function enablePush() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    state.value = permission === 'denied' ? 'blocked' : 'off'
    return
  }
  const registration = await navigator.serviceWorker.ready
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: await serverKey(),
    }))
  await register(subscription)
  state.value = 'on'
}

/**
 * Stops push in this browser. Also run on sign-out, so the next person here doesn't get this person's
 * notifications. If the backend can't be told, the push service will tell it the browser is gone.
 */
export async function disablePush() {
  if (!isSupported()) return
  const subscription = await currentSubscription()
  if (subscription) {
    await api
      .DELETE('/api/me/push-subscriptions', { params: { query: { endpoint: subscription.endpoint } } })
      .catch(() => {})
    await subscription.unsubscribe()
  }
  state.value = 'off'
}

// the next person on this browser mustn't get this person's notifications
beforeLogout(disablePush)

export function usePushSubscription() {
  return { state: readonly(state), enable: enablePush, disable: disablePush, refresh: readPushState }
}
