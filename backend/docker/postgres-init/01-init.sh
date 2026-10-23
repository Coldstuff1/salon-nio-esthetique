#!/bin/sh
# Runs once, on an empty data volume. Creates Keycloak's database and, when NIO_DB_USER is set (production), a
# separate non-superuser role that owns the application's database. In dev the backend connects as the
# bootstrap user from POSTGRES_USER instead.
set -eu

create_owned_database() { # role password database
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v role="$1" -v password="$2" -v db="$3" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL
}

create_owned_database keycloak "${KEYCLOAK_DB_PASSWORD:?}" keycloak

if [ -n "${NIO_DB_USER:-}" ]; then
    create_owned_database "$NIO_DB_USER" "${NIO_DB_PASSWORD:?}" "${NIO_DB_NAME:?}"
fi
