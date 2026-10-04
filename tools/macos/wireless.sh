#!/bin/bash
source "$(dirname "$0")/common.sh"
exec node "$BamiRoot/tools/macos/tailscale-service.cjs" "$@"
