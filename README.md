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

> Keycloak stores its data in a `keycloak` database created by `backend/docker/postgres-init/01-keycloak.sql`. Postgres only runs init scripts on an empty volume, so if you had the stack running before Keycloak was added, recreate the volume once with `docker compose down -v`.
