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

For local development, `./gradlew bootRun` in `backend/` starts Postgres and Keycloak through Spring Boot's Docker Compose support, and `npm run dev` in `frontend/` serves the SPA on port 5173.

## Authentication (Keycloak)

The SPA logs in with OAuth2 Authorization Code + PKCE (`keycloak-js`) and calls `/api` with the access token (JWT) as a Bearer token. The backend is a stateless OAuth2 resource server that maps Keycloak realm roles to Spring `ROLE_*` authorities (`client` → `ROLE_CLIENT`, etc.).

- Admin console: http://localhost:8180 (`admin` / `admin`)
- Realm `nio` is imported from `backend/docker/keycloak/realm-nio.json` on first start.
- Dev users (password `password`): `client1`, `employee1`, `admin1`
- New users get the `client` role by default.

### Login theme

Every login page (sign in, register, forgot password, …) uses the custom `nio` theme in `backend/docker/keycloak/themes/nio/login/`, mounted into the container. It extends Keycloak's unstyled `base` theme: `template.ftl` (the shared layout) and `login.ftl` are overridden, every other page keeps the base markup and is styled through the `kc*Class` mappings in `theme.properties` and `resources/css/nio.css`. The brand tokens in `nio.css` are a copy of `frontend/src/assets/main.css` — update both together. Custom strings live in `messages/messages_{fr,en}.properties`.

`start-dev` disables theme caching, so edits show up on page refresh. The realm JSON selects the theme, but it is only imported when the realm doesn't exist yet; on an existing volume either run `docker compose down -v` or set *Realm settings → Themes → Login theme* to `nio` in the admin console.

> Keycloak stores its data in a `keycloak` database created by `backend/docker/postgres-init/01-keycloak.sql`. Postgres only runs init scripts on an empty volume, so if you had the stack running before Keycloak was added, recreate the volume once with `docker compose down -v`.
