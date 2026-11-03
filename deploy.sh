#!/usr/bin/env bash
#
# Installs dependencies, builds the API and UI, and restarts all containers.
# Stories and PDFs are kept: the Docker volumes are never removed.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: bash deploy.sh [--local] [--help]

Builds both images, which installs dependencies and compiles inside Docker with
the pinned JDK and Node versions, then restarts the containers. Only Docker is
required. The running containers are left alone if a build fails.

  --local   First install and build on this machine too, running the API unit
            tests. Requires JDK 25 and a Node version Angular supports
            (see FFreader-UI/.nvmrc).
  --help    Show this message.

Settings such as FFREADER_PORT are read from the environment or a .env file.
EOF
}

local_build=false
for arg in "$@"; do
    case "$arg" in
        --local) local_build=true ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; usage >&2; exit 2 ;;
    esac
done

step() {
    printf '\n==> %s\n' "$1"
}

require() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Error: $1 is not installed or not on PATH." >&2
        exit 1
    fi
}

cd "$(dirname "${BASH_SOURCE[0]}")"

require docker
if ! docker compose version >/dev/null 2>&1; then
    echo "Error: the Docker Compose plugin is not available." >&2
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "Error: cannot reach the Docker daemon. Is Docker running?" >&2
    exit 1
fi

if [ "$local_build" = true ]; then
    require java
    require npm

    step "UI: installing dependencies"
    (cd FFreader-UI && npm ci)

    step "UI: building"
    (cd FFreader-UI && npm run build)

    # Run through sh because mvnw isn't marked executable in git.
    step "API: installing dependencies, running unit tests and building"
    (cd FFreader-API && sh ./mvnw -B clean package)
fi

step "Docker: installing dependencies and building images"
docker compose build

step "Docker: restarting containers"
docker compose up -d --force-recreate --remove-orphans --wait --wait-timeout 300

step "Done"
docker compose ps
echo
echo "Open http://localhost:${FFREADER_PORT:-$(sed -n 's/^FFREADER_PORT=//p' .env 2>/dev/null | tail -n 1 | grep . || echo 8081)}"
