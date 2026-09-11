#!/usr/bin/env python3
"""Не даёт продуктовым экранам снова начать рисовать базовые контролы напрямую.

Разрешённый слой для поведения/внешнего вида контролов: studio/ui/BrainUi.kt.
MaterialTheme/Text/Surface допустимы как токены и примитивы отображения, но кнопки,
поля, переключатели и слайдеры должны приходить только из Brain UI.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / "composeApp/src/commonMain/kotlin/brain/studio"
ALLOWED = {(ROOT / "ui/BrainUi.kt").resolve()}
PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])(?:Button|IconButton|TextField|OutlinedTextField|Switch|Checkbox|RadioButton|Slider|RangeSlider|BasicTextField)\s*\("
)

violations = []
for path in ROOT.rglob("*.kt"):
    if path.resolve() in ALLOWED:
        continue
    text = path.read_text(encoding="utf-8")
    for number, line in enumerate(text.splitlines(), 1):
        if PATTERN.search(line):
            violations.append(f"{path.relative_to(ROOT.parents[4])}:{number}: {line.strip()}")

if violations:
    print("Базовые контролы должны использоваться только через Brain UI:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("Brain UI boundary: OK")
