import createClient, { type Middleware } from 'openapi-fetch'
import { keycloak, login } from '@/auth/keycloak'
import type { components, paths } from './schema'

/** Request/response shapes from the backend's OpenAPI spec, e.g. `Schemas['BookingView']`. */
export type Schemas = components['schemas']

const auth: Middleware = {
  async onRequest({ request }) {
    if (keycloak.authenticated) {
      await keycloak.updateToken(30)
      request.headers.set('Authorization', `Bearer ${keycloak.token}`)
    }
    return request
  },
  async onResponse({ response }) {
    if (response.status === 401) {
      await login()
    }
    return response
  },
}

/** Typed client generated from `openapi.json`; run `npm run gen:api` after backend changes. */
export const api = createClient<paths>()
api.use(auth)
