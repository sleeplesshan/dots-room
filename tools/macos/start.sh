#!/bin/bash
source "$(dirname "$0")/common.sh"
cd "$BamiRoot"
[[ -z "${ANDROID_SERIAL:-}" ]] || "$BamiRoot/tools/macos/setup-usb.sh"
exec node tools/macos/process.cjs start "${1:-browser-dots}"
