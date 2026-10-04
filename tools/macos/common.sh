#!/bin/bash
set -euo pipefail
BamiRoot="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools:$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin"
command -v node >/dev/null && [[ "$(node -p 'process.versions.node.split(".")[0]')" == 24 ]] || { echo 'Node 24 is required.' >&2;exit 1; }
export BAMI_PORT="${BAMI_PORT:-$(cd "$BamiRoot" && node -e 'console.log(JSON.parse(require("fs").readFileSync("config.local.json","utf8")).port||8788)' 2>/dev/null || echo 8788)}"
[[ "$BAMI_PORT" =~ ^[0-9]+$ ]] && (( BAMI_PORT>=1024 && BAMI_PORT<=65535 )) || exit 1
[[ "$BAMI_PORT" != 8765 && "$BAMI_PORT" != 8787 ]] || exit 1
bami_device() {
 [[ -n "${ANDROID_SERIAL:-}" ]] || { echo 'Select a USB serial using ANDROID_SERIAL.' >&2;exit 1; }
 local BamiLine
 BamiLine="$(adb devices -l | awk -v serial="$ANDROID_SERIAL" '$1==serial {print}')"
 [[ "$BamiLine" == *' device '* && "$BamiLine" == *'usb:'* ]] || { echo 'Selected USB device is not authorized.' >&2;exit 1; }
 BamiUsbKey="$(printf '%s' "$ANDROID_SERIAL" | shasum -a 256 | awk '{print $1}')"
 BamiUsbMarker="$BamiRoot/.state/usb/$BamiUsbKey-$BAMI_PORT"
}
