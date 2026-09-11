#!/usr/bin/env python3
"""Запрещает случайно добавить облачный AI, аналитику или синхронизацию в runtime продукта."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SCOPES = [ROOT / 'kashaCore', ROOT / 'composeApp', ROOT / 'runtime', ROOT / 'desktopApp']
SKIP = {'build', 'test', 'commonTest', 'node_modules'}
FORBIDDEN = re.compile(
    r'(firebase|firestore|supabase|sentry|segment\.io|amplitude|mixpanel|appcenter|'
    r'api\.openai\.com|api\.anthropic\.com|generativelanguage\.googleapis\.com|icloud|cloudkit)',
    re.IGNORECASE,
)
violations = []
for scope in SCOPES:
    if not scope.exists():
        continue
    for path in scope.rglob('*'):
        if not path.is_file() or any(part in SKIP for part in path.relative_to(scope).parts):
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if FORBIDDEN.search(line):
                violations.append(f'{path.relative_to(ROOT)}:{number}: {line.strip()}')

if violations:
    print('Kasha должна оставаться local-only. Найдены запрещённые облачные/telemetry ссылки:', file=sys.stderr)
    print('\n'.join(violations), file=sys.stderr)
    sys.exit(1)
print('Local-only boundary: OK')
