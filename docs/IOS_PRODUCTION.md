# Kasha iOS — production plan

Этот документ дополняет каноническое `docs/SPEC.md` и фиксирует границу iOS shell.

## Цель

iOS не получает отдельную бизнес-логику или отдельный интерфейс. Приложение состоит из общего `kashaCore`, общего `aiCatalog`, общего Compose UI и тонких iOS-адаптеров.

## Что должно быть настоящим на iOS

- запись с микрофона в локальный файл;
- pause/resume/stop и реальный уровень сигнала;
- восстановление незавершённой записи;
- локальное постоянное хранение данных и аудиофайлов;
- воспроизведение, пауза, продолжение, позиция и скорость;
- локальные уведомления задач;
- защищённое хранение API keys для optional external AI;
- реальный AI adapter без переноса AI-логики в UI/platform shell.

## Что остаётся общим

- проекты, заметки, задачи и capture lifecycle;
- сортировки и manual order;
- правила сроков/напоминаний;
- три AI-роли и privacy contracts;
- Kasha UI и Kasha Icons;
- выбор AI engines/providers;
- проверки AI-результатов.

## Граница AI

Локальный AI на iOS подключается через те же Core contracts, что desktop/runtime. Конкретная iOS inference-технология является заменяемым platform adapter и не должна попадать в `kashaCore`.

External AI допустим только после explicit consent; API keys хранятся в iOS Keychain.

## Definition of Done

Production iOS считается готовым, когда тестовая `IosTestRepository`/заглушки recorder/audio удалены из основного app entry, реальный voice → text → note/task flow работает на устройстве, данные переживают перезапуск, уведомления доставляются системой, а iOS CI собирает framework и IPA с production adapters.
