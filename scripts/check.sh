#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/check_contract.py
swift test
swift test -c release
if ! command -v xcodebuild >/dev/null; then
  echo 'Алгоритмы и конфигурация проверены. Сборка iOS, SwiftData, аудио и UI здесь не запускались: требуется Mac с Xcode 26+.'
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
xcrun simctl bootstatus "$DEVICE" -b
# Уникальное имя позволяет повторить проверку без удаления предыдущих результатов.
RESULT="TestResults-$(date +%Y%m%d-%H%M%S)-$$.xcresult"
xcodebuild -project ThirdBrain.xcodeproj -scheme ThirdBrain -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE,arch=$(uname -m)" -destination-timeout 60 \
  -parallel-testing-enabled NO -test-timeouts-enabled YES -maximum-test-execution-time-allowance 90 \
  -derivedDataPath DerivedData -resultBundlePath "$RESULT" \
  CODE_SIGNING_ALLOWED=NO test-without-building
xcodebuild -project ThirdBrain.xcodeproj -scheme ThirdBrain -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath DerivedDataDevice \
  CODE_SIGNING_ALLOWED=NO build
