import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import { api } from './api/http'
import { initAuth, keycloak } from './auth/keycloak'
import { syncPushSubscription } from './composables/usePushSubscription'
import { i18n } from './i18n'
import { registerServiceWorker } from './pwa'

await initAuth()
// Imported only now: the router reads the URL when it's created, and Keycloak has just removed its login
// response (#state=…&code=…) from it. Loading it after also means its guards know who is signed in.
const { router } = await import('./router')
registerServiceWorker(router)
if (keycloak.authenticated) {
  // tells the backend how to reach this person (email, name, roles), for their notifications
  api.GET('/api/me').catch(() => {})
  void syncPushSubscription()
}

createApp(App).use(i18n).use(router).mount('#app')
