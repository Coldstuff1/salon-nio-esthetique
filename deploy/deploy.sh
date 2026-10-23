#!/usr/bin/env bash
# Deploys one commit: deploy/deploy.sh <full commit sha>. CI runs it over SSH with a key that can run nothing
# else (the sha then arrives in SSH_ORIGINAL_COMMAND); run it by hand to roll back to an earlier commit.
# Backs up the databases, checks the commit out (compose file, Caddyfile), pulls the images CI built and pushed
# for it, and waits until every service is healthy. If they aren't, it goes back to the previous commit and
# exits with an error. See docs/operations.md, "Continuous deployment".
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/lib.sh

release() {
    git checkout --quiet --detach "$1" \
        && echo "NIO_IMAGE_TAG=$1" > .env.release \
        && stack pull --quiet \
        && stack up -d --no-build --wait --wait-timeout 600
}

# the checkout replaces this file while it runs: bash has parsed the whole function before it starts
main() {
    local sha="${1:-${SSH_ORIGINAL_COMMAND:-}}"
    [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Usage: deploy/deploy.sh <full commit sha>" >&2; exit 2; }

    git fetch --quiet origin
    git cat-file -e "$sha^{commit}"

    # first automated deploy: there is no previous release to go back to
    [ -f .env.release ] || echo "NIO_IMAGE_TAG=$sha" > .env.release
    local previous
    previous="$(sed -n 's/^NIO_IMAGE_TAG=//p' .env.release)"

    # Flyway migrations only go forward: this snapshot is the way back for the data
    deploy/backup.sh pre-deploy

    if release "$sha"; then
        # keep the images of this release and the previous one; older ones can be pulled again
        docker image ls --filter 'reference=ghcr.io/coldstuff1/nio-*' --format '{{.Repository}}:{{.Tag}}' \
            | grep -v -e ":$sha\$" -e ":$previous\$" \
            | xargs -r docker image rm || true
        docker image prune -f >/dev/null
        echo "Deployed $sha."
        exit 0
    fi

    echo "Deploying $sha failed; going back to $previous." >&2
    stack ps >&2 || true
    [ "$previous" = "$sha" ] || release "$previous" || echo "Rolling back failed too: check the server." >&2
    exit 1
}

main "$@"; exit
