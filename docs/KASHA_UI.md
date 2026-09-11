# Kasha UI

Kasha UI — единственный продуктовый UI-слой Kasha поверх Compose Foundation/UI. Он общий для Android, iOS, Desktop и Web и не должен визуально превращаться в Material Design.

## Граница

Продуктовые экраны не создают кнопки, поля, переключатели, слайдеры или навигацию напрямую. Эти элементы берутся только из `composeApp/src/commonMain/kotlin/brain/studio/ui`.

`MaterialTheme` используется как контейнер токенов цвета и типографики. CI запускает `scripts/check-ui-boundary.py` и запрещает:

- базовые Material-контролы вне Kasha UI;
- Material Icons, SF Symbols, Lucide и другие параллельные icon packs;
- возврат старых `BrainUi` / `BrainNavigation`.

## Компоненты

- `KashaButton` — primary/secondary + hover/pressed/focus/disabled;
- `KashaIconButton` — компактное действие;
- `KashaQuietButton` — тихое текстовое действие;
- `KashaField` — общее поле ввода/read-only;
- `KashaEditableNote` — название + тело заметки;
- `KashaSwitchRow`;
- `KashaSlider`;
- `KashaPanel`;
- `KashaListCard`;
- `KashaNavigationItem`;
- `KashaWaveform`;
- `KashaProcessingRing`;
- `KashaCaptureMark`;
- `KashaSortBar`;
- `KashaDestinationSwitch`;
- `KashaReorderableList` — manual order через long-press + drag.

`KashaSortBar` и `KashaDestinationSwitch` — взаимоисключающие segmented controls. В Compose они объявлены через `selectableGroup` + `selectable` с `Role.RadioButton` и публикуют выбранное состояние, а не являются набором обычных кнопок.

## Иконки

Единственный продуктовый источник геометрии — Phosphor. Для motion используются открытые MIT-реализации animated Phosphor, зафиксированные в `third_party`. Иконка в состоянии покоя остаётся Phosphor; анимация запускается только как реакция на взаимодействие и не должна быть постоянным декором.

## Типографика и тема

- **Commissioner** — основной UI-текст;
- отдельное display-начертание Commissioner 600 с очень небольшим `FLAR=10` — крупные заголовки;
- сборка автоматически проверяет кириллицу и все восемь языков Kasha, включая казахские `Ә Ғ Қ Ң Ө Ұ Ү Һ`;
- основной текст 15–17sp;
- нижняя навигация и вспомогательные подписи не меньше 11sp;
- display scale ограничен 46sp, чтобы не быть desktop-only;
- light — почти белый с едва жёлтым/бумажным смещением;
- dark — почти чёрный с едва коричневым смещением.

## Принцип

Экран отвечает за продуктовый сценарий. Геометрия контролов, motion, typography, цвета, interaction states и iconography принадлежат Kasha UI. Если одно и то же решение появляется на двух экранах — его нужно поднимать в Kasha UI, а не копировать.
