#!/usr/bin/env python3
import argparse
import datetime as dt
import json
from pathlib import Path

RELEASE_BASE = "https://github.com/vvverman/Kasha/releases/download/sidestore-latest"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ipa", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--build", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    ipa = Path(args.ipa)
    if not ipa.is_file() or ipa.stat().st_size <= 0:
        raise SystemExit("IPA не найден или пуст")

    today = dt.datetime.now(dt.timezone.utc).date().isoformat()
    source = {
        "name": "Kasha Test",
        "subtitle": "Бесплатные тестовые iOS-сборки Kasha",
        "apps": [
            {
                "name": "Kasha Test",
                "bundleIdentifier": "ru.vrmn.kasha.test",
                "developerName": "Vyacheslav Verman",
                "subtitle": "Kasha без моделей — для тестирования интерфейса и сценариев",
                "localizedDescription": (
                    "Тестовая iOS-сборка Kasha. Общее Kasha Core и Kasha UI настоящие; "
                    "AI и аудио в этой бесплатной сборке работают через локальные заглушки. "
                    "Аккаунт, облачный backend и внешние AI API не используются."
                ),
                "iconURL": f"{RELEASE_BASE}/Kasha-icon.png",
                "tintColor": "#191715",
                "category": "utilities",
                "versions": [
                    {
                        "version": args.version,
                        "buildVersion": str(args.build),
                        "date": today,
                        "localizedDescription": "Автоматическая тестовая сборка из актуального Kasha.",
                        "downloadURL": f"{RELEASE_BASE}/Kasha.ipa",
                        "size": ipa.stat().st_size,
                        "minOSVersion": "16.0",
                    }
                ],
                "appPermissions": {
                    "entitlements": [],
                    "privacy": {},
                },
            }
        ],
        "news": [],
    }

    Path(args.output).write_text(
        json.dumps(source, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
