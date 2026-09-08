#!/usr/bin/env python3
"""Проверки статических ограничений MVP. Не заменяют проверку сети на iPhone."""
import pathlib
import plistlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
info = plistlib.loads((ROOT / "ThirdBrain/Info.plist").read_bytes())
assert info.get("NSMicrophoneUsageDescription"), "Нет пояснения доступа к микрофону"
assert info.get("NSSpeechRecognitionUsageDescription"), "Нет пояснения распознавания"
assert info.get("UIBackgroundModes") == ["audio"], "Неожиданные фоновые возможности"
project = (ROOT / "ThirdBrain.xcodeproj/project.pbxproj").read_text()
assert "XCRemoteSwiftPackageReference" not in project, "Сторонний пакет вне Apple-only MVP"
assert "com.apple.developer.icloud" not in project.lower(), "iCloud не входит в MVP"
assert "IPHONEOS_DEPLOYMENT_TARGET = 26.0;" in project
allowed = {"Foundation", "FoundationModels", "AVFoundation", "Speech", "SwiftUI", "SwiftData", "Observation"}
for path in (ROOT / "ThirdBrain").rglob("*.swift"):
    code = path.read_text()
    imports = set(re.findall(r"^import\s+(\w+)", code, re.M))
    assert imports <= allowed, (path, imports - allowed)
    assert not re.search(r"\b(URLSession|SFSpeechRecognizer)\b", code), f"Проверить возможное серверное распознавание: {path}"
assert "cloudKitDatabase: .none" in (ROOT / "ThirdBrain/LocalStore.swift").read_text()
privacy = plistlib.loads((ROOT / "ThirdBrain/PrivacyInfo.xcprivacy").read_bytes())
assert privacy.get("NSPrivacyTracking") is False
assert not privacy.get("NSPrivacyTrackingDomains")
ET.parse(ROOT / "ThirdBrain.xcodeproj/xcshareddata/xcschemes/ThirdBrain.xcscheme")
print("Проверки конфигурации MVP пройдены: Apple-only, локальное хранилище, разрешения, схема Xcode.")
