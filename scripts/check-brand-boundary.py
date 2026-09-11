#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = re.compile(r"(?:3rd|third)brain", re.IGNORECASE)
SKIP_DIRS = {'.git', '.gradle', 'build', 'studio-output', 'test-output', 'node_modules', '.studio-python'}
violations = []

for path in ROOT.rglob('*'):
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        continue
    if FORBIDDEN.search(str(rel)):
        violations.append(f'path: {rel}')
    if not path.is_file():
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if FORBIDDEN.search(line):
            violations.append(f'{rel}:{number}: {line.strip()}')

if violations:
    print('Legacy brand references found:', file=sys.stderr)
    print('\n'.join(violations), file=sys.stderr)
    sys.exit(1)
print('Brand boundary: OK')
