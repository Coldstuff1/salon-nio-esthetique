import Keycloak, { type KeycloakLoginOptions } from 'keycloak-js'
import { computed, readonly, ref } from 'vue'
import { useTheme } from '@/composables/useTheme'

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

const beforeLogoutHooks: (() => Promise<void>)[] = []

/** Runs `hook` when the person signs out, while their token still works; its failure doesn't stop the sign-out. */
export function beforeLogout(hook: () => Promise<void>) {
  beforeLogoutHooks.push(hook)
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

/**
 * keycloak.login(), but the login page is told the site's theme choice (light/dark/system):
 * it lives on another origin, so it can't read the site's localStorage. See the nio login theme's template.ftl.
 */
export async function login(options?: KeycloakLoginOptions) {
  const url = new URL(await keycloak.createLoginUrl(options))
  url.searchParams.set('nio_theme', useTheme().preference.value)
  location.assign(url)
}

export function useAuth() {
  return {
    authenticated: readonly(authenticated),
    username: readonly(username),
    roles: readonly(roles),
    isAdmin: computed(() => roles.value.includes('admin')),
    hasRole: (role: string) => roles.value.includes(role),
    // open Keycloak's login page in the site's current language, coming back to `path` (default: here)
    login: (path?: string) =>
      login({
        locale: document.documentElement.lang,
        redirectUri: path ? new URL(path, location.origin).href : undefined,
      }),
    logout: async () => {
      await Promise.all(beforeLogoutHooks.map((hook) => hook().catch(() => {})))
      await keycloak.logout({ redirectUri: location.origin })
    },
  }
}
