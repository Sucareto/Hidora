#!/bin/sh
exec docker compose -f "$(dirname "$0")/compose.yaml" run --rm --build apk "$@"
