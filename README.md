# salon-nio-esthetique

## Running the stack

```sh
docker compose up --build
```

| Service  | URL                     |
|----------|-------------------------|
| Frontend | http://localhost:5173   |
| Backend  | http://localhost:8080   |
| Keycloak | http://localhost:8180   |
| Mailpit  | http://localhost:8025   |

Every port is bound to `127.0.0.1`, so the dev stack isn't reachable from the network.

For local development, `./gradlew bootRun` in `backend/` starts Postgres, Keycloak, Mailpit and SeaweedFS through Spring Boot's Docker Compose support, and `npm run dev` in `frontend/` serves the SPA on port 5173.

Production runs a different stack (`compose.prod.yaml`, behind Caddy with TLS); see [docs/operations.md](docs/operations.md).

## Authentication (Keycloak)

The SPA logs in with OAuth2 Authorization Code + PKCE (`keycloak-js`) and calls `/api` with the access token (JWT) as a Bearer token. The backend is a stateless OAuth2 resource server that maps Keycloak realm roles to Spring `ROLE_*` authorities (`client` → `ROLE_CLIENT`, etc.). It only accepts tokens whose audience includes `nio-backend`, which the `nio-frontend` client adds.

- Admin console: http://localhost:8180 (`admin` / `admin`)
- Realm `nio` is imported from `backend/docker/keycloak/realm-nio-dev.json` on first start.
- Dev users (password `nio-dev-password`): `client1`, `employee1`, `admin1`
- New users get the `client` role by default and must verify their email before booking; the verification email lands in Mailpit.

The realm comes in two files. `realm-nio.json` is the production realm, baked into the Keycloak image: no users, URLs and secrets read from the environment, reCAPTCHA on registration and a required one-time code for employees and admins. `realm-nio-dev.json` adds the dev users and the Swagger UI client, points at localhost and Mailpit, and keeps Keycloak's stock sign-in flow (no one-time code locally). Every other setting (password policy, lockout, roles, clients) is the same in both; change it in both.

> **Upgrading an existing dev stack:** the realm is only imported when it doesn't exist yet, and the backend now rejects tokens without the `nio-backend` audience. Recreate the volumes once with `docker compose down -v` (this also wipes the dev database) so the new realm is imported.

### Login theme

Every login page (sign in, register, forgot password, …) uses the custom `nio` theme in `backend/docker/keycloak/themes/nio/login/`, mounted into the container. It extends Keycloak's unstyled `base` theme: `template.ftl` (the shared layout) and `login.ftl` are overridden, every other page keeps the base markup and is styled through the `kc*Class` mappings in `theme.properties` and `resources/css/nio.css`. The brand tokens in `nio.css` are a copy of `frontend/src/assets/main.css` — update both together. Custom strings live in `messages/messages_{fr,en}.properties`.

`start-dev` disables theme caching, so edits show up on page refresh. The realm JSON selects the theme, but it is only imported when the realm doesn't exist yet; on an existing volume either run `docker compose down -v` or set *Realm settings → Themes → Login theme* to `nio` in the admin console.

> Keycloak stores its data in a `keycloak` database created by `backend/docker/postgres-init/01-init.sh`. Postgres only runs init scripts on an empty volume, so if you had the stack running before Keycloak was added, recreate the volume once with `docker compose down -v`.
