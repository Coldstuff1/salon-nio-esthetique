import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import { initAuth } from './auth/keycloak'
import { i18n } from './i18n'

await initAuth()

createApp(App).use(i18n).mount('#app')
