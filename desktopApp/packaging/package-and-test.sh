#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$PWD/macos-output"
APP="$PWD/desktopApp/build/compose/binaries/main/app/3rdBrain.app"
mkdir -p "$OUT"
[ -d "$APP" ]
# Gradle копирует ресурсы как данные и может сбросить executable bit.
# Восстанавливаем его в готовом bundle ДО подписи, не на компьютере пользователя.
RES="$APP/Contents/app/resources"
for name in whisper-cli llama-completion ffmpeg; do
 test -f "$RES/bin/$name"
 chmod 755 "$RES/bin/$name"
 test -x "$RES/bin/$name"
done
{
 ls -l "$RES/bin"
 cat "$APP/Contents/app/3rdBrain.cfg"
} > "$OUT/launcher-config.txt"
# После jpackage подписываем вложенные Mach-O и затем весь bundle. Это ad-hoc, не Developer ID.
while IFS= read -r -d '' file; do
 if /usr/bin/file -b "$file" | grep -q 'Mach-O'; then
  /usr/bin/codesign --force --sign - --timestamp=none "$file"
 fi
done < <(find "$APP/Contents" -type f -print0)
/usr/bin/codesign --force --deep --sign - --timestamp=none --entitlements desktopApp/packaging/entitlements.plist "$APP"
/usr/bin/codesign --verify --deep --strict --verbose=2 "$APP"
/usr/libexec/PlistBuddy -c 'Print :NSMicrophoneUsageDescription' "$APP/Contents/Info.plist"
# Убираем сборочные модели: приложение должно использовать только свою копию.
rm -rf desktopApp/bundle/common
if [ -d desktopApp/build/native ]; then mv desktopApp/build/native desktopApp/build/native-not-on-path; fi
TEST_HOME="$OUT/clean-home"
mkdir -p "$TEST_HOME"
# Минимальный PATH: нет brew, cmake, java или внешних моделей; внешняя сеть запрещена sandbox-exec.
env -i HOME="$TEST_HOME" PATH=/usr/bin:/bin:/usr/sbin:/sbin TMPDIR="${TMPDIR:-/tmp}" \
 /usr/bin/sandbox-exec -p '(version 1)(allow default)(deny network*)' \
 "$APP/Contents/MacOS/3rdBrain" --self-test "$OUT/self-test" "$PWD/model-test/russian.wav" > "$OUT/self-test.log" 2>&1
# Настоящее окно приложения, без запроса несуществующего микрофона CI.
env -i HOME="$TEST_HOME" PATH=/usr/bin:/bin:/usr/sbin:/sbin TMPDIR="${TMPDIR:-/tmp}" \
 THIRDBRAIN_HOME="$OUT/ui-data" "$APP/Contents/MacOS/3rdBrain" --ui-smoke "$OUT" > "$OUT/ui.log" 2>&1 &
PID=$!
for n in {1..40}; do
 [ -f "$OUT/ui-ready.txt" ] && break
 kill -0 "$PID" 2>/dev/null || { cat "$OUT/ui.log"; exit 1; }
 sleep 1
done
[ -f "$OUT/ui-ready.txt" ]
/usr/sbin/screencapture -x "$OUT/macos-window.png" || true
wait "$PID"
STAGE="$OUT/volume"
mkdir -p "$STAGE"
mv "$APP" "$STAGE/3rdBrain.app"
ln -s /Applications "$STAGE/Applications"
cp desktopApp/packaging/Установка.txt "$STAGE/Установка.txt"
hdiutil create -volname '3rdBrain' -srcfolder "$STAGE" -ov -format UDZO "$OUT/3rdBrain-1.0.0-macOS-arm64.dmg"
hdiutil verify "$OUT/3rdBrain-1.0.0-macOS-arm64.dmg"
(cd "$OUT" && shasum -a 256 3rdBrain-1.0.0-macOS-arm64.dmg > SHA256SUMS.txt)
MOUNT="$OUT/mounted"
mkdir -p "$MOUNT"
hdiutil attach -nobrowse -readonly -mountpoint "$MOUNT" "$OUT/3rdBrain-1.0.0-macOS-arm64.dmg"
codesign --verify --deep --strict "$MOUNT/3rdBrain.app"
test -x "$MOUNT/3rdBrain.app/Contents/app/resources/bin/whisper-cli"
test -x "$MOUNT/3rdBrain.app/Contents/app/resources/bin/llama-completion"
test -x "$MOUNT/3rdBrain.app/Contents/app/resources/bin/ffmpeg"
hdiutil detach "$MOUNT"
python3 - <<'PY'
import json, pathlib, platform
out=pathlib.Path('macos-output')
dmg=out/'3rdBrain-1.0.0-macOS-arm64.dmg'
report={'passed':True,'file':dmg.name,'bytes':dmg.stat().st_size,'architecture':platform.machine(),
        'macOS':platform.mac_ver()[0],'bundledJava':True,'bundledModels':['Whisper Small','Qwen3-4B Q4_K_M'],
        'externalNetworkDeniedDuringInference':True,'developerIdSigned':False,'notarized':False,
        'microphoneHardwareTested':False,'ui':(out/'ui-ready.txt').read_text(),
        'selfTest':json.loads((out/'self-test/self-test.json').read_text())}
(out/'BUILD-REPORT.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
PY
