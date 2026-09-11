# Kasha UI — иконки

## Единственный визуальный набор

В продуктовом UI Kasha разрешены только **Phosphor Icons**.

Запрещено добавлять параллельные наборы Material Icons, SF Symbols, Lucide, Tabler, Hugeicons и любые локально нарисованные заменители.

Состояние покоя каждой продуктовой иконки берётся из точной геометрии **Phosphor Fill**. Единый map геометрии находится в `composeApp/src/commonMain/kotlin/brain/studio/ui/PhosphorFillPaths.kt`.

## Анимация

Анимация не должна менять принадлежность иконки к Phosphor. React/Motion не входят в runtime Kasha: motion-паттерны переносятся в Compose Multiplatform.

Канонический внешний snapshot анимированных Phosphor:

- `smammar100/Iconimate`
- commit `a5d64350968f87155ac6fb1656351adee0273de0`
- MIT
- полный snapshot вместе с registry и лицензией сохранён локально в `third_party/iconimate/upstream`.

Дополнительный reference для прямых Phosphor-анимаций:

- `ln-dev7/icons-animated`
- commit `4d8269768ccfc73c09b773b90be8158cb4f21f4c`
- MIT
- provenance и лицензия сохранены в `third_party/icons-animated`.

## Правила motion

- Анимация запускается только как реакция на взаимодействие: hover, press, focus, drag или явное изменение состояния.
- Бесконечные декоративные циклы для обычных иконок не использовать.
- Предпочитать короткие движения 300–500 ms.
- Если upstream-анимация рассчитана на stroke-глиф с несколькими частями, а продукт использует единый Fill-path, переносить смысл движения консервативно на целый Phosphor-глиф; не дорисовывать новую геометрию.
- Для pencil сохранён upstream wiggle `0 → -3° → +3° → 0`.
- Для gear сохранён поворот `0 → 180°` за 0.5 s.
- Для directional arrows используется короткое направленное смещение.
- Для trash сохраняется lift/compression-язык оригинальной анимации.

## Архитектурная граница

Продуктовые экраны не должны хранить SVG/path-данные и не должны импортировать сторонние icon libraries. Они используют только `Glyph` + `KashaIcon`/контролы Kasha UI.

Все новые иконки сначала добавляются в единый Phosphor-слой, затем используются экранами. Это позволяет одинаково отрисовывать и анимировать их на Desktop, Android, iOS и Web.
