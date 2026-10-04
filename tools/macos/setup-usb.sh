#!/bin/bash
source "$(dirname "$0")/common.sh"
bami_device
BamiMapping="$(adb -s "$ANDROID_SERIAL" reverse --list | awk -v port="tcp:$BAMI_PORT" '$2==port {print $3}')"
if [[ -n "$BamiMapping" ]];then [[ "$BamiMapping" == "tcp:$BAMI_PORT" ]] || { echo '이 포트의 다른 매핑을 변경하지 않습니다.' >&2;exit 1; };echo '선택 기기의 기존 동일 reverse를 재사용합니다.';else
 adb -s "$ANDROID_SERIAL" reverse "tcp:$BAMI_PORT" "tcp:$BAMI_PORT"
 mkdir -p "$BamiRoot/.state/usb"
 touch "$BamiUsbMarker"
fi
adb -s "$ANDROID_SERIAL" reverse --list
