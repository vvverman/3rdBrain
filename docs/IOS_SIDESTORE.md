# Kasha Test на iPhone без Apple Developer Program

Для личного тестирования Kasha не использует TestFlight. GitHub Actions собирает **unsigned IPA**, а SideStore подписывает приложение обычным Apple Account непосредственно для устройства пользователя.

## Что публикует GitHub

Workflow: `.github/workflows/ios-sidestore.yml`

После изменений iOS/Core/UI в `main` он:

1. собирает `KashaShared.framework` из общего Compose/Kasha Core;
2. собирает тонкую iOS-оболочку без code signing;
3. упаковывает `Kasha.ipa`;
4. генерирует AltSource/SideStore `source.json`;
5. обновляет один rolling GitHub Release `sidestore-latest`.

Постоянные адреса:

- source: `https://github.com/vvverman/Kasha/releases/download/sidestore-latest/source.json`
- IPA: `https://github.com/vvverman/Kasha/releases/download/sidestore-latest/Kasha.ipa`
- icon: `https://github.com/vvverman/Kasha/releases/download/sidestore-latest/Kasha-icon.png`
- one-click source: `sidestore://source?url=https%3A%2F%2Fgithub.com%2Fvvverman%2FKasha%2Freleases%2Fdownload%2Fsidestore-latest%2Fsource.json`

Каждый workflow run получает новый `buildVersion`, поэтому SideStore видит новую сборку как обновление при неизменном source URL.

## Почему IPA unsigned

GitHub не хранит Apple ID, сертификат или provisioning profile. При бесплатном Apple Account это и не требуется: SideStore сам подписывает IPA для конкретного устройства и отвечает за периодическое обновление подписи.

## Текущая iOS Test-сборка

Это лёгкая сборка без Whisper/Qwen:

- используется настоящий общий `kashaCore`;
- используется настоящий общий Kasha UI;
- проекты, заметки, задачи, сроки и архив сохраняются локально;
- AI/STT и аудиофайл заменены явными локальными demo-заглушками;
- никаких внешних AI API и облачного backend нет.

Это позволяет проверять продуктовый интерфейс и сценарии на iPhone до подключения настоящих iOS audio/AI adapters.

## Архитектурная граница

`iosApp` не содержит бизнес-правил. SwiftUI-оболочка только создаёт `UIViewController` из `KashaShared`. Тестовые iOS adapters находятся в `composeApp/src/iosMain` и реализуют те же Core-порты, что desktop/web adapters.

Следующий этап production iOS — заменить demo recorder/audio/intelligence/storage adapters на нативные реализации, не меняя Core и продуктовые экраны.
