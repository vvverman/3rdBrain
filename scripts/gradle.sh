#!/usr/bin/env bash
# Минимальный загрузчик закреплённого Gradle. Это не официальный Gradle Wrapper.
set -euo pipefail
VERSION=9.3.1
SHA256=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/kasha-bootstrap"
GRADLE="$CACHE/gradle-$VERSION/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  command -v curl >/dev/null || { echo "Нужен curl" >&2; exit 1; }
  command -v unzip >/dev/null || { echo "Нужен unzip" >&2; exit 1; }
  mkdir -p "$CACHE"
  WORK=$(mktemp -d "$CACHE/download.XXXXXX")
  trap 'rm -rf "$WORK"' EXIT
  curl --fail --location --retry 3 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$WORK/gradle.zip"
  if command -v sha256sum >/dev/null; then ACTUAL=$(sha256sum "$WORK/gradle.zip" | cut -d ' ' -f1)
  else ACTUAL=$(shasum -a 256 "$WORK/gradle.zip" | cut -d ' ' -f1); fi
  [ "$ACTUAL" = "$SHA256" ] || { echo "Контрольная сумма Gradle не совпала. Запуск отменён." >&2; exit 1; }
  unzip -q "$WORK/gradle.zip" -d "$WORK"
  if [ ! -d "$CACHE/gradle-$VERSION" ]; then mv "$WORK/gradle-$VERSION" "$CACHE/"; fi
fi
exec "$GRADLE" "$@"
