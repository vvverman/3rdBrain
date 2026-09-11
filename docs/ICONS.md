# Kasha UI — иконки

## Единственный визуальный набор

В продуктовом UI Kasha разрешены только **Phosphor Icons**.

Запрещено добавлять параллельные наборы Material Icons, SF Symbols, Lucide, Tabler, Hugeicons и любые локально нарисованные заменители.

Состояние покоя каждой продуктовой иконки берётся из точной геометрии **Phosphor Fill**. Единый map геометрии находится в `composeApp/src/commonMain/kotlin/brain/studio/ui/PhosphorFillPaths.kt`.

## Канонические источники анимации

React/Motion не входят в runtime Kasha: motion-паттерны переносятся в Compose Multiplatform, но сам характер движения нельзя придумывать заново.

Основной snapshot:

- `smammar100/Iconimate`;
- commit `a5d64350968f87155ac6fb1656351adee0273de0`;
- MIT;
- полный registry и лицензия сохранены в `third_party/iconimate/upstream`.

Дополнительный reference прямых Phosphor-анимаций:

- `ln-dev7/icons-animated`;
- commit `4d8269768ccfc73c09b773b90be8158cb4f21f4c`;
- MIT;
- provenance и лицензия сохранены в `third_party/icons-animated`.

## Жёсткое правило motion

У иконки есть только два допустимых состояния реализации:

1. **точно портированная upstream-анимация** — движение, тайминг и смысл привязаны к конкретному исходнику;
2. **статичный Phosphor** — если точный порт ещё не сделан.

Запрещено заполнять пробелы «похожим», «restrained» или просто красивым motion собственного сочинения.

Сейчас прямые Compose-порты включены для:

- `BACK / NEXT / UP / DOWN` — directional travel из `ln-dev7/icons-animated`, 40 units в viewport 256 за 0.4 s;
- `SETTINGS` — gear, поворот 180° за 0.5 s;
- `EDIT` — pencil wiggle `0 → -3° → +3° → 0` за 0.4 s.

`HOME`, `TASKS`, `DELETE`, `PLUS`, `CHECK` и остальные glyphs остаются статичными, пока их составные upstream-анимации не будут перенесены буквально. Например, Iconimate `house`, `list` и `trash` двигают отдельные части и/или используют path morph/clip; заменять это обычным scale/rotate всего Fill-глифа нельзя.

## Поведение

- Motion запускается только реакцией на взаимодействие: hover, press, focus, drag или явное изменение состояния.
- Бесконечные декоративные циклы для обычных иконок запрещены.
- Reduced motion должен оставлять корректный статичный Phosphor.
- Состояние покоя всегда совпадает с канонической Phosphor-геометрией.

## Архитектурная граница

Продуктовые экраны не хранят SVG/path-данные и не импортируют сторонние icon libraries. Они используют только `Glyph` + `KashaIcon`/контролы Kasha UI.

Новая иконка сначала добавляется в единый Phosphor-слой. Если для неё нужен motion, рядом должен существовать конкретный upstream-source и точный Compose-порт; иначе иконка остаётся статичной. Это правило одинаково для Desktop, Android, iOS и Web.

`scripts/check-ui-boundary.py` автоматически проверяет этот whitelist в CI: неподтверждённый motion считается нарушением границы Kasha UI. Guard выполняется до компиляции продуктового UI, поэтому такой motion блокирует PR сразу, а не обнаруживается только на визуальном тестировании.
