#!/usr/bin/env python3
from pathlib import Path

browser = Path("tests/studio_browser.py")
text = browser.read_text(encoding="utf-8")

old_current = "def current():return next((c for c in api('snapshot')['captures'] if c['noteId'] is None),None)"
new_current = "def current():return next((c for c in api('snapshot')['captures'] if c['noteId'] is None and c.get('taskId') is None),None)"
if old_current not in text and new_current not in text:
    raise SystemExit("browser current() marker not found")
text = text.replace(old_current, new_current)

old_nav = "for name in ['Главная','Проекты','Настройки']:"
new_nav = "for name in ['Главная','Проекты','Задачи','Настройки']:"
if old_nav not in text and new_nav not in text:
    raise SystemExit("browser navigation marker not found")
text = text.replace(old_nav, new_nav)

marker = "        wait(lambda:current() is None,'удаление текущего');checks.append('создание тестового сигнала и удаление')\n        for language in ['ru','en','es','fr','de','uk','be','kk']:"
block = """        wait(lambda:current() is None,'удаление текущего');checks.append('создание тестового сигнала и удаление')

        # Задачи: два голосовых capture -> отдельные задачи -> все четыре сортировки
        # -> long-press reorder -> смена сортировки -> возврат к сохранённому Manual.
        def task_y(label):
            box=page.get_by_text(label,exact=True).bounding_box();assert box,label
            return box['y']
        tab('Главная');button('Попробовать без микрофона');ready()
        field('Текст заметки','Альфа задача');button('Отправить в проект')
        click('radio','Задача');click('button',re.compile('^Твой первый проект'));button('Сохранить задачу')
        wait(lambda:current() is None,'первая задача сохранена')
        tab('Главная');button('Попробовать без микрофона');ready()
        field('Текст заметки','Бета задача');button('Отправить в проект')
        click('radio','Задача');click('button',re.compile('^Твой первый проект'));button('Сохранить задачу')
        wait(lambda:current() is None,'вторая задача сохранена')
        tasks=api('snapshot')['tasks'];assert len(tasks)==2,tasks
        assert {t['text'] for t in tasks}=={'Альфа задача','Бета задача'}
        tab('Задачи');page.get_by_text('Альфа задача',exact=True).wait_for(state='visible');page.get_by_text('Бета задача',exact=True).wait_for(state='visible')
        assert task_y('Бета задача') < task_y('Альфа задача')
        click('radio','А-Я');wait(lambda:task_y('Альфа задача') < task_y('Бета задача'),'алфавитная сортировка задач')
        click('radio','Создано');wait(lambda:task_y('Бета задача') < task_y('Альфа задача'),'сортировка задач по созданию')
        click('radio','Изменено');wait(lambda:task_y('Бета задача') < task_y('Альфа задача'),'сортировка задач по изменению')
        click('radio','Вручную');wait(lambda:task_y('Альфа задача') < task_y('Бета задача'),'исходный ручной порядок задач')
        alpha=page.get_by_text('Альфа задача',exact=True).bounding_box();beta=page.get_by_text('Бета задача',exact=True).bounding_box();assert alpha and beta
        page.mouse.move(alpha['x']+alpha['width']/2,alpha['y']+alpha['height']/2);page.mouse.down();page.wait_for_timeout(700)
        page.mouse.move(beta['x']+beta['width']/2,beta['y']+beta['height']/2,steps=14);page.wait_for_timeout(180);page.mouse.up()
        wait(lambda:task_y('Бета задача') < task_y('Альфа задача'),'ручная перестановка задач')
        manual_ids=[t['id'] for t in sorted(api('snapshot')['tasks'],key=lambda t:t['manualOrder'])]
        by_text={t['text']:t['id'] for t in api('snapshot')['tasks']};assert manual_ids==[by_text['Бета задача'],by_text['Альфа задача']],manual_ids
        click('radio','А-Я');wait(lambda:task_y('Альфа задача') < task_y('Бета задача'),'переключение с Manual')
        click('radio','Вручную');wait(lambda:task_y('Бета задача') < task_y('Альфа задача'),'восстановление ручного порядка задач')
        screen('tasks-manual');checks.append('задачи из голоса, четыре сортировки, long-press reorder и сохранение Manual')

        for language in ['ru','en','es','fr','de','uk','be','kk']:"""
if "задачи из голоса, четыре сортировки" not in text:
    if marker not in text:
        raise SystemExit("browser insertion marker not found")
    text = text.replace(marker, block)
browser.write_text(text, encoding="utf-8")

spec = Path("docs/SPEC.md")
spec_text = spec.read_text(encoding="utf-8")
old_policy = "Основной внешний источник motion зафиксирован на конкретном commit `ln-dev7/icons-animated`; для отсутствующих glyphs допускается аккуратный motion в том же временном и пластическом языке, но геометрия остаётся Phosphor."
new_policy = "Источники motion фиксируются на конкретных commit `smammar100/Iconimate` и `ln-dev7/icons-animated`. Для конкретного glyph допустим только буквальный Compose-порт подтверждённой upstream-анимации; пока такого порта нет, Phosphor-глиф остаётся статичным. Придумывать заменяющий «похожий» motion запрещено."
if old_policy not in spec_text and new_policy not in spec_text:
    raise SystemExit("SPEC icon policy marker not found")
spec.write_text(spec_text.replace(old_policy, new_policy), encoding="utf-8")

print("Final refactor patch: OK")
