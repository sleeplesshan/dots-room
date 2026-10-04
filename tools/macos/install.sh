#!/bin/bash
source "$(dirname "$0")/common.sh"
bami_device
adb -s "$ANDROID_SERIAL" install -r "$BamiRoot/apps/android/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$ANDROID_SERIAL" shell am start -n dev.dots.room/.MainActivity
