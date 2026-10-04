#!/bin/bash
source "$(dirname "$0")/common.sh"
bami_device
cd "$BamiRoot"
read -r -p "Dots Room 앱과 선택 태블릿 전용 자격 증명을 저장하려면 PAIR: " BamiConsent
[[ "$BamiConsent" == PAIR ]] || exit 0
exec node tools/macos/apply-approved-pairing.cjs --approved
