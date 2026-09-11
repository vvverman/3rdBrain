#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
BUILD=yes
OPEN=no
for arg in "$@"; do
  case "$arg" in --no-build) BUILD=no;; --open) OPEN=yes;; *) echo "Неизвестный параметр: $arg" >&2; exit 1;; esac
done
if [ "$(uname -s)" = Darwin ] && [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  if [ -z "$JAVA_HOME" ] && command -v brew >/dev/null; then
    PREFIX=$(brew --prefix openjdk@21 2>/dev/null || true)
    [ -z "$PREFIX" ] || JAVA_HOME="$PREFIX/libexec/openjdk.jdk/Contents/Home"
  fi
  if [ -n "$JAVA_HOME" ]; then export JAVA_HOME; fi
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA" >/dev/null || { echo "Нужна JDK 21. На Mac: brew install openjdk@21" >&2; exit 1; }
"$JAVA" -version >/dev/null 2>&1 || { echo "Не удалось запустить Java. Установите JDK 21." >&2; exit 1; }
# Собственные настройки окружения имеют приоритет над подготовленным набором.
ENV_FILE="${KASHA_TOOLS_HOME:-$HOME/.kasha-tools}/models.env"
if [ -f "$ENV_FILE" ] && [ -z "${KASHA_WHISPER_CLI:-}${KASHA_WHISPER_MODEL:-}${KASHA_LLAMA_CLI:-}${KASHA_LLAMA_MODEL:-}" ]; then
  source "$ENV_FILE"
fi
export KASHA_WEB_ROOT="$PWD/composeApp/build/dist/wasmJs/productionExecutable"
if [ "$BUILD" = yes ]; then bash scripts/gradle.sh :composeApp:wasmJsBrowserDistribution :runtime:installDist; fi
[ -f runtime/build/install/runtime/bin/runtime ] || { echo "Сначала запустите без --no-build" >&2; exit 1; }
echo "Kasha: http://127.0.0.1:8787 . Для остановки нажмите Ctrl+C."
if [ "$OPEN" = yes ]; then
  (
    for attempt in {1..60}; do
      if curl -fsS http://127.0.0.1:8787/api/health >/dev/null 2>&1; then
        if [ "$(uname -s)" = Darwin ]; then open http://127.0.0.1:8787
        elif command -v xdg-open >/dev/null; then xdg-open http://127.0.0.1:8787; fi
        exit 0
      fi
      sleep 1
    done
  ) &
fi
exec bash runtime/build/install/runtime/bin/runtime
