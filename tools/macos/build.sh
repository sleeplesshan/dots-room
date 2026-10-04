#!/bin/bash
source "$(dirname "$0")/common.sh"
cd "$BamiRoot"
npm ci --ignore-scripts
npm run build
npm test
npm run assets:prepare
npm run assets:check
apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
