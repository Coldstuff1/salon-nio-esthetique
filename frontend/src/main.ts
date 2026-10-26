import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import { api } from './api/http'
import { initAuth, keycloak } from './auth/keycloak'
import { i18n } from './i18n'

await initAuth()
// tells the backend how to reach this person (email, name, roles), for their notifications
if (keycloak.authenticated) api.GET('/api/me').catch(() => {})
// Imported only now: the router reads the URL when it's created, and Keycloak has just removed its login
// response (#state=…&code=…) from it. Loading it after also means its guards know who is signed in.
const { router } = await import('./router')

createApp(App).use(i18n).use(router).mount('#app')
