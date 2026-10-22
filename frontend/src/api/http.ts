import { keycloak } from '@/auth/keycloak'

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)

  if (keycloak.authenticated) {
    await keycloak.updateToken(30)
    headers.set('Authorization', `Bearer ${keycloak.token}`)
  }

  const response = await fetch(path, { ...init, headers })
  if (response.status === 401) {
    await keycloak.login()
  }
  return response
}
