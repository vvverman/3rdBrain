#!/usr/bin/env python3
"""Не даёт продуктовым экранам обходить Kasha UI или возвращать чужие иконки."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / "composeApp/src/commonMain/kotlin/brain/studio"
ALLOWED_CONTROLS = {(ROOT / "ui/KashaUi.kt").resolve()}
CONTROL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])(?:Button|IconButton|TextField|OutlinedTextField|Switch|Checkbox|RadioButton|Slider|RangeSlider|BasicTextField)\s*\("
)
FORBIDDEN_ICON_PATTERNS = (
    "androidx.compose.material.icons",
    "Icons.Default",
    "Icons.Filled",
    "Icons.Outlined",
    "lucide",
    "SF Symbols",
    "SFSymbol",
)

violations = []
for path in ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if path.resolve() not in ALLOWED_CONTROLS:
        for number, line in enumerate(text.splitlines(), 1):
            if CONTROL_PATTERN.search(line):
                violations.append(f"{path.relative_to(ROOT.parents[4])}:{number}: базовый контрол вне Kasha UI: {line.strip()}")
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith(("//", "/*", "*", "*/")):
            continue
        if any(pattern.lower() in line.lower() for pattern in FORBIDDEN_ICON_PATTERNS):
            violations.append(f"{path.relative_to(ROOT.parents[4])}:{number}: запрещённый источник иконок: {line.strip()}")

legacy = list(ROOT.rglob("*BrainUi*")) + list(ROOT.rglob("*BrainNavigation*"))
for path in legacy:
    violations.append(f"{path.relative_to(ROOT.parents[4])}: legacy Brain UI должен быть удалён")

if violations:
    print("Kasha UI boundary нарушен:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("Kasha UI + Phosphor boundary: OK")
