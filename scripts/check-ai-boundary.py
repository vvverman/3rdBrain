#!/usr/bin/env python3
"""Проверяет, что внешний AI остаётся сменным, consented и не тащит секреты в Core state."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
STUDIO = ROOT / 'kashaCore/src/commonMain/kotlin/brain/studio/Studio.kt'
AI = ROOT / 'kashaCore/src/commonMain/kotlin/brain/studio/AiEngines.kt'
REQUESTS = ROOT / 'kashaCore/src/commonMain/kotlin/brain/studio/AiRequests.kt'
SETTINGS = ROOT / 'composeApp/src/commonMain/kotlin/brain/studio/AiSettings.kt'

errors = []

def data_class_header(text: str, name: str) -> str:
    match = re.search(rf'data class {re.escape(name)}\((.*?)\)\s*(?:\{{|$)', text, re.S)
    return match.group(1) if match else ''

studio = STUDIO.read_text(encoding='utf-8')
ai = AI.read_text(encoding='utf-8')
requests = REQUESTS.read_text(encoding='utf-8')
settings = SETTINGS.read_text(encoding='utf-8')

for class_name, body in (
    ('Preferences', data_class_header(studio, 'Preferences')),
    ('CloudAiConnection', data_class_header(ai, 'CloudAiConnection')),
):
    if not body:
        errors.append(f'Missing serializable state class: {class_name}')
        continue
    for token in ('apiKey', 'api_key', 'secret', 'accessToken', 'bearerToken'):
        if re.search(re.escape(token), body, re.IGNORECASE):
            errors.append(f'{class_name} must not persist secret field: {token}')

required_ai_fragments = (
    'enum class AiRole { SPEECH_TO_TEXT, TEXT, ROUTING }',
    'const val CONSENT_VERSION',
    'privacyConsentVersion',
    'interface SpeechToTextEngine',
    'interface TextProcessingEngine',
    'interface RoutingEngine',
    'interface CloudAiGateway',
)
for fragment in required_ai_fragments:
    if fragment not in ai:
        errors.append(f'Missing AI boundary contract: {fragment}')

if 'val apiKey: String? = null' not in requests:
    errors.append('API key may only cross Core as an explicit transient request payload')
if 'privacyConsentVersion = AiPrivacy.CONSENT_VERSION' not in settings:
    errors.append('Cloud connection UI must stamp current privacy consent version')
if not re.search(r'val\s+canSave\s*=\s*cloud\.available\s*&&\s*consent', settings):
    errors.append('Cloud connection actions must be gated by platform availability and explicit consent')
if settings.count('enabled = canSave') < 2:
    errors.append('Both cloud test and save actions must use the consent gate')

# Секрет разрешён только как transient argument/request. Не допускаем его в сериализуемых
# Preferences и метаданных; реальные secure-store реализации живут в platform shell.
for path in (ROOT / 'kashaCore').rglob('*.kt'):
    text = path.read_text(encoding='utf-8')
    rel = path.relative_to(ROOT).as_posix()
    if 'apiKey' in text and rel not in {
        'kashaCore/src/commonMain/kotlin/brain/studio/AiEngines.kt',
        'kashaCore/src/commonMain/kotlin/brain/studio/AiRequests.kt',
    }:
        errors.append(f'API key leaked into unrelated Core file: {rel}')

if errors:
    print('AI boundary violations:', file=sys.stderr)
    print('\n'.join(f'- {e}' for e in errors), file=sys.stderr)
    sys.exit(1)
print('AI boundary: OK (role ports, explicit consent, no persisted Core secrets)')
