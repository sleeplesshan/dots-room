#!/bin/bash
source "$(dirname "$0")/common.sh"
cd "$BamiRoot"
node tools/macos/process.cjs stop
node tools/macos/tailscale-service.cjs stop
[[ -z "${ANDROID_SERIAL:-}" ]] || "$BamiRoot/tools/macos/teardown-usb.sh"
