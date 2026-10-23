# Shared by the deploy scripts: settings from .env.prod, restic in a container, the production stack (images
# tagged with the commit in .env.release, which deploy.sh writes).
# Sourced from the repository root.

setting() { sed -n "s/^$1=//p" .env.prod | tail -n 1; }

restic() {
    docker run --rm -i \
        -e RESTIC_REPOSITORY="$(setting RESTIC_REPOSITORY)" \
        -e RESTIC_PASSWORD="$(setting RESTIC_PASSWORD)" \
        -e AWS_ACCESS_KEY_ID="$(setting BACKUP_AWS_ACCESS_KEY_ID)" \
        -e AWS_SECRET_ACCESS_KEY="$(setting BACKUP_AWS_SECRET_ACCESS_KEY)" \
        -e AWS_DEFAULT_REGION="$(setting NIO_S3_REGION)" \
        restic/restic:0.19.1 "$@"
}

stack() { docker compose -f compose.prod.yaml --env-file .env.prod --env-file .env.release "$@"; }
