#!/usr/bin/env bash
# Сборка закреплённых локальных движков и проверенная загрузка весов. Без облачного inference.
set -euo pipefail
umask 077
ROOT="${THIRDBRAIN_TOOLS_HOME:-$HOME/.3rdbrain-tools}"
mkdir -p "$ROOT"
ROOT=$(cd "$ROOT" && pwd)
LOCK="$ROOT/.setup-lock"
mkdir "$LOCK" 2>/dev/null || { echo "Настройка уже запущена. После аварийного завершения удалите $LOCK" >&2; exit 1; }
trap 'rmdir "$LOCK" 2>/dev/null || true' EXIT
for tool in git cmake curl ffmpeg; do
  command -v "$tool" >/dev/null || { echo "Не найден $tool. На Mac: brew install cmake ffmpeg. Затем повторите запуск." >&2; exit 1; }
done
JOBS="${THIRDBRAIN_BUILD_JOBS:-4}"
WHISPER_REV=306c88f4d1286aec1bf96e544632897886af5501
LLAMA_REV=5266f24da75dc449bd56cbed7addb9c8e4a6a73e
build_tool() {
  local repo="$1" rev="$2" target="$3"
  local dir="$ROOT/$repo-$rev"
  if [ ! -x "$dir/build/bin/$target" ]; then
    mkdir -p "$dir"
    if [ ! -d "$dir/.git" ]; then git -C "$dir" init -q; git -C "$dir" remote add origin "https://github.com/ggml-org/$repo.git"; fi
    git -C "$dir" fetch --depth 1 origin "$rev"
    git -C "$dir" checkout --detach FETCH_HEAD
    [ "$(git -C "$dir" rev-parse HEAD)" = "$rev" ] || exit 1
    cmake -S "$dir" -B "$dir/build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_CUDA=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_SERVER=OFF -DWHISPER_BUILD_TESTS=OFF
    cmake --build "$dir/build" --config Release -j "$JOBS" --target "$target"
  fi
}
sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d ' ' -f1
  else shasum -a 256 "$1" | cut -d ' ' -f1; fi
}
fetch_model() {
  local name="$1" url="$2" expected="$3" file="$ROOT/models/$1"
  if [ -f "$file" ] && [ "$(sha256 "$file")" = "$expected" ]; then return; fi
  echo "Загрузка модели: $name"
  curl --fail --location --retry 3 --connect-timeout 30 --max-time 1800 "$url" -o "$file.part"
  [ "$(sha256 "$file.part")" = "$expected" ] || { echo "Неверная контрольная сумма: $name" >&2; exit 1; }
  mv "$file.part" "$file"
}
build_tool whisper.cpp "$WHISPER_REV" whisper-cli
build_tool llama.cpp "$LLAMA_REV" llama-completion
mkdir -p "$ROOT/models"
fetch_model ggml-small.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/90a64d80ea254cf67575b41a5971f972c79f7b45/ggml-small.bin \
  1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b
fetch_model Qwen3-4B-Q4_K_M.gguf \
  https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/a9a60d009fa7ff9606305047c2bf77ac25dbec49/Qwen3-4B-Q4_K_M.gguf \
  7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5
# Пути экранированы для bash, файл доступен только владельцу. Чужие модели не удаляем.
{
  printf 'export THIRDBRAIN_WHISPER_CLI=%q\n' "$ROOT/whisper.cpp-$WHISPER_REV/build/bin/whisper-cli"
  printf 'export THIRDBRAIN_WHISPER_MODEL=%q\n' "$ROOT/models/ggml-small.bin"
  printf 'export THIRDBRAIN_LLAMA_CLI=%q\n' "$ROOT/llama.cpp-$LLAMA_REV/build/bin/llama-completion"
  printf 'export THIRDBRAIN_LLAMA_MODEL=%q\n' "$ROOT/models/Qwen3-4B-Q4_K_M.gguf"
  printf 'export THIRDBRAIN_FFMPEG=%q\n' "$(command -v ffmpeg)"
} > "$ROOT/models.env.tmp"
mv "$ROOT/models.env.tmp" "$ROOT/models.env"
echo "Модели готовы. Настройки: $ROOT/models.env"
