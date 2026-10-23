import Keycloak from 'keycloak-js'
import { computed, readonly, ref } from 'vue'

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
})

const authenticated = ref(false)
const username = ref<string>()
const roles = ref<string[]>([])

function syncState() {
  authenticated.value = !!keycloak.authenticated
  username.value = keycloak.tokenParsed?.preferred_username
  roles.value = keycloak.tokenParsed?.realm_access?.roles ?? []
}

keycloak.onAuthSuccess = syncState
keycloak.onAuthRefreshSuccess = syncState
keycloak.onAuthLogout = syncState
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => keycloak.clearToken())
}

export async function initAuth() {
  try {
    await keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
    })
  } catch (error) {
    console.error('Keycloak initialization failed', error)
  }
  syncState()
}

export function useAuth() {
  return {
    authenticated: readonly(authenticated),
    username: readonly(username),
    roles: readonly(roles),
    isAdmin: computed(() => roles.value.includes('admin')),
    hasRole: (role: string) => roles.value.includes(role),
    // open Keycloak's login page in the site's current language
    login: () => keycloak.login({ locale: document.documentElement.lang }),
    logout: () => keycloak.logout({ redirectUri: location.origin }),
  }
}
