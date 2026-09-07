#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
swift test
if ! command -v xcodebuild >/dev/null; then
  echo 'Основные тесты завершены. Для сборки iOS требуется Mac с Xcode 26+.'
  exit 0
fi
xcodebuild -version
xcodebuild -project ThirdBrain.xcodeproj -scheme ThirdBrain -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build-for-testing
DEVICE=$(xcrun simctl list devices available --json | python3 -c '
import json,sys
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if "iOS-26" not in runtime: continue
    for device in devices:
        if device.get("isAvailable") and device["name"].startswith("iPhone"):
            print(device["udid"]); sys.exit(0)
sys.exit("Не найден доступный симулятор iPhone с iOS 26")
')
xcodebuild -project ThirdBrain.xcodeproj -scheme ThirdBrain -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE" -parallel-testing-enabled NO \
  -derivedDataPath DerivedData -resultBundlePath TestResults.xcresult \
  CODE_SIGNING_ALLOWED=NO test-without-building
