#!/usr/bin/env bash
# Только CI/Linux: запускается внутри unshare --net после установки инструментов и моделей.
set -euo pipefail
ip link set lo up
python - <<'PY'
import socket
try:
    connection = socket.create_connection(('1.1.1.1', 443), timeout=2)
except OSError:
    print('Внешняя сеть недоступна. Проверка идёт только через loopback.')
else:
    connection.close()
    raise SystemExit('Изоляция сети не сработала')
PY
source "$KASHA_TOOLS_HOME/models.env"
export KASHA_HOME=$(mktemp -d)
export HF_HUB_OFFLINE=1
bash runtime/build/install/runtime/bin/runtime > models-runtime.log 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT
python tests/real_models.py 2>&1 | tee real-models.log
