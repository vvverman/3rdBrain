#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# На macOS находим установленную JDK 21, не меняя настройки системы.
if [ "$(uname -s)" = Darwin ] && [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  if [ -n "$JAVA_HOME" ]; then export JAVA_HOME; fi
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA" >/dev/null || { echo "Установите JDK 21 и повторите запуск." >&2; exit 1; }
export THIRDBRAIN_WEB_ROOT="$PWD/composeApp/build/dist/wasmJs/productionExecutable"
if [ "${1:-}" != --no-build ]; then
  bash scripts/gradle.sh :composeApp:wasmJsBrowserDistribution :runtime:installDist
fi
[ -f runtime/build/install/runtime/bin/runtime ] || { echo "Сначала запустите без --no-build" >&2; exit 1; }
echo "Откройте http://127.0.0.1:8787 . Для остановки нажмите Ctrl+C."
exec bash runtime/build/install/runtime/bin/runtime
