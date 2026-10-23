#!/usr/bin/env bash
# Nightly backup: dumps every database (the app's and Keycloak's, roles included), then restic encrypts it and
# uploads it to the backup bucket, keeping 30 daily and 12 monthly snapshots. Run it on the server from a
# systemd timer or cron; restore with deploy/restore.sh (docs/operations.md).
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/lib.sh

# the first run creates the (encrypted) repository
restic cat config >/dev/null 2>&1 || restic init

stack exec -T postgres pg_dumpall --clean --if-exists --username postgres \
    | gzip \
    | restic backup --stdin --stdin-filename nio-databases.sql.gz --tag nightly
restic forget --keep-daily 30 --keep-monthly 12 --prune
