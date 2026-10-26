/**
 * What the service worker (src/sw.ts) tells open tabs; src/pwa.ts handles it. Shared by both, which are
 * type-checked separately (tsconfig.sw.json).
 */
export type WorkerMessage =
  /** A push notification was just shown. */
  | { type: 'notification-received' }
  /** The person clicked one; the tab should open its page. */
  | { type: 'notification-click'; id: string; link: string | null }
