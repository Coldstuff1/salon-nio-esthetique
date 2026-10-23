import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import { initAuth } from './auth/keycloak'
import { i18n } from './i18n'

await initAuth()
// Imported only now: the router reads the URL when it's created, and Keycloak has just removed its login
// response (#state=…&code=…) from it. Loading it after also means its guards know who is signed in.
const { router } = await import('./router')

createApp(App).use(i18n).use(router).mount('#app')
