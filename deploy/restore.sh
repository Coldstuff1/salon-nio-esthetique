#!/usr/bin/env bash
# Replaces every database with a backup: deploy/restore.sh [snapshot id, default latest]
# List snapshots first with: source deploy/lib.sh && restic snapshots
# The backend and Keycloak are stopped meanwhile, so the site is down for the minute this takes.
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/lib.sh
snapshot="${1:-latest}"

read -r -p "Replace ALL production data with backup '$snapshot'? Type 'restore' to continue: " answer
[ "$answer" = restore ] || { echo "Cancelled."; exit 1; }

stack stop backend keycloak
# the dump drops and recreates each database; "cannot drop the currently open database" and
# "current user cannot be dropped" are expected notices, not failures
restic dump "$snapshot" nio-databases.sql.gz \
    | gunzip \
    | stack exec -T postgres psql --quiet --username postgres --dbname postgres
stack start keycloak backend
echo "Restored '$snapshot'. Check the site, then sign in to confirm."
