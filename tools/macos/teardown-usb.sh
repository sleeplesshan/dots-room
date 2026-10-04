#!/bin/bash
source "$(dirname "$0")/common.sh"
bami_device
if [[ -f "$BamiUsbMarker" ]];then
 BamiMapping="$(adb -s "$ANDROID_SERIAL" reverse --list | awk -v port="tcp:$BAMI_PORT" '$2==port {print $3}')"
 [[ "$BamiMapping" == "tcp:$BAMI_PORT" ]] && adb -s "$ANDROID_SERIAL" reverse --remove "tcp:$BAMI_PORT"
 rm "$BamiUsbMarker"
else echo '이 스크립트가 만든 매핑이 없어 제거하지 않습니다.';fi
