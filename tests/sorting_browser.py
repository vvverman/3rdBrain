"""Дополнительный E2E: проекты/заметки и сохранность Manual после reload."""
import json
import os
import pathlib
import re
import shutil
import time
import urllib.request
from contextlib import suppress
from playwright.sync_api import sync_playwright

BASE = 'http://127.0.0.1:8787'
OUT = pathlib.Path('test-output/sorting')
OUT.mkdir(parents=True, exist_ok=True)


def api(path, data=None, method=None):
    headers = {'X-Kasha-Client': 'web'}
    if data is not None:
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(
        BASE + '/api/' + path,
        data=json.dumps(data).encode() if data is not None else None,
        headers=headers,
        method=method,
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


for _ in range(60):
    try:
        api('health')
        break
    except Exception:
        time.sleep(1)
else:
    raise AssertionError('Сервис не запустился')

prefs = api('preferences')
prefs.update(autoRecord=False, language='ru', theme='light')
api('preferences', prefs, 'PUT')

with sync_playwright() as pw:
    executable = os.getenv('CHROME_PATH') or shutil.which('google-chrome') or shutil.which('chromium')
    browser = pw.chromium.launch(
        executable_path=executable,
        headless=True,
        args=['--no-sandbox', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
    )
    page = browser.new_page(viewport={'width': 1280, 'height': 1000}, locale='ru-RU')
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def wait(check, description, seconds=30):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            value = check()
            if value:
                return value
            page.wait_for_timeout(100)
        raise AssertionError('Не дождались: ' + description)

    def role_locator(role, name):
        return page.get_by_role(role, name=name) if hasattr(name, 'search') else page.get_by_role(role, name=name, exact=True)

    def role_click(role, name):
        locator = role_locator(role, name)
        locator.wait_for(state='visible', timeout=30000)
        box = locator.bounding_box()
        assert box and box['height'] > 0, name
        page.mouse.click(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2)
        page.wait_for_timeout(220)

    def button(name):
        role_click('button', name)

    def tab(name):
        button(name)

    def text_click(name):
        locator = page.get_by_text(name, exact=True)
        locator.wait_for(state='visible', timeout=30000)
        box = locator.bounding_box()
        assert box and box['height'] > 0, name
        page.mouse.click(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2)
        page.wait_for_timeout(250)

    def field(label, value):
        locator = page.get_by_role('textbox', name=label, exact=True)
        locator.wait_for(state='visible', timeout=30000)
        box = locator.bounding_box()
        assert box, label
        page.mouse.click(box['x'] + min(20, box['width'] / 2), box['y'] + min(20, box['height'] / 2))
        page.wait_for_timeout(160)
        page.keyboard.press('Control+a')
        page.wait_for_timeout(90)
        page.keyboard.press('Backspace')
        page.wait_for_timeout(90)
        page.keyboard.press('Control+a')
        page.wait_for_timeout(60)
        page.keyboard.insert_text(value)
        page.wait_for_timeout(650)

    def item(label):
        # Compose WASM объединяет содержимое карточки в одно accessible-name.
        # Не проверяем count заранее: карточка может появиться после ближайшей recomposition.
        return page.get_by_role('button', name=re.compile(r'^' + re.escape(label) + r'(?:\s|$)')).first

    def item_y(label):
        locator = item(label)
        locator.wait_for(state='visible', timeout=30000)
        box = locator.bounding_box()
        assert box, label
        return box['y']

    def drag(source, target):
        source_locator = item(source)
        target_locator = item(target)
        source_locator.wait_for(state='visible', timeout=30000)
        target_locator.wait_for(state='visible', timeout=30000)
        a = source_locator.bounding_box()
        b = target_locator.bounding_box()
        assert a and b, (source, target)
        page.mouse.move(a['x'] + a['width'] / 2, a['y'] + a['height'] / 2)
        page.mouse.down()
        page.wait_for_timeout(700)
        page.mouse.move(b['x'] + b['width'] / 2, b['y'] + b['height'] / 2, steps=16)
        page.wait_for_timeout(180)
        page.mouse.up()
        page.wait_for_timeout(350)

    def current():
        return next((c for c in api('snapshot')['captures'] if c['noteId'] is None and c.get('taskId') is None), None)

    def ready():
        return wait(lambda: (c if (c := current()) and c['status'] == 'READY' else None), 'готовый demo capture')

    def make_note(title, body, project_title='Твой первый проект'):
        tab('Главная')
        button('Попробовать без микрофона')
        ready()
        field('Название заметки', title)
        field('Текст заметки', body)
        button('Отправить в проект')
        role_click('button', re.compile(r'^' + re.escape(project_title)))
        button('Новая заметка')
        wait(lambda: current() is None, 'сохранение заметки ' + title)

    try:
        page.goto(BASE, wait_until='networkidle', timeout=60000)
        page.locator('canvas').first.wait_for(state='visible')
        page.get_by_role('button', name='Главная', exact=True).wait_for(state='visible')

        # Основной E2E перед этим уже создал второй проект и две задачи.
        existing_titles = {p['title'] for p in api('snapshot')['projects']}
        assert {'Твой первый проект', 'Рабочие идеи'} <= existing_titles, existing_titles

        # --- Проекты: все четыре режима + ручная перестановка ---
        tab('Проекты')
        button('Новый проект')
        field('Название проекта', 'Альфа проект')
        field('Что сюда складывать', 'Проект для проверки сортировки')
        button('Сохранить')
        for title in ['Твой первый проект', 'Рабочие идеи', 'Альфа проект']:
            item(title).wait_for(state='visible', timeout=30000)

        text_click('А-Я')
        wait(lambda: item_y('Альфа проект') < item_y('Рабочие идеи') < item_y('Твой первый проект'), 'алфавитная сортировка проектов')
        text_click('Создано')
        wait(lambda: item_y('Альфа проект') < item_y('Рабочие идеи') < item_y('Твой первый проект'), 'сортировка проектов по созданию')
        text_click('Изменено')
        wait(lambda: item_y('Альфа проект') < item_y('Рабочие идеи') < item_y('Твой первый проект'), 'сортировка проектов по изменению')
        text_click('Вручную')
        wait(lambda: item_y('Твой первый проект') < item_y('Рабочие идеи') < item_y('Альфа проект'), 'исходный Manual проектов')
        drag('Альфа проект', 'Твой первый проект')
        wait(lambda: item_y('Альфа проект') < item_y('Твой первый проект') < item_y('Рабочие идеи'), 'ручная перестановка проектов')
        text_click('А-Я')
        wait(lambda: item_y('Альфа проект') < item_y('Рабочие идеи') < item_y('Твой первый проект'), 'выход из Manual проектов')
        text_click('Вручную')
        wait(lambda: item_y('Альфа проект') < item_y('Твой первый проект') < item_y('Рабочие идеи'), 'восстановление Manual проектов')

        snapshot = api('snapshot')
        project_manual = [p['title'] for p in sorted(snapshot['projects'], key=lambda p: p['manualOrder'])]
        assert project_manual[:3] == ['Альфа проект', 'Твой первый проект', 'Рабочие идеи'], project_manual

        # --- Заметки: создаём две дополнительные в одном проекте ---
        make_note('Якорь заметка', 'Якорь заметка для проверки ручного порядка.')
        make_note('Альфа заметка', 'Альфа заметка для проверки сортировки.')

        # В основном E2E старая заметка закреплена. Для чистого сравнения sort-mode
        # снимаем pin; вне Manual закрепление намеренно имеет приоритет.
        tab('Проекты')
        role_click('button', re.compile(r'^Твой первый проект'))
        role_click('button', re.compile(r'^Отредактированная заметка'))
        button('Открепить')
        button('Править')
        field('Текст заметки', 'Текст обновлён последним специально для проверки даты изменения.')
        button('Сохранить')
        button('Назад')

        for title in ['Отредактированная заметка', 'Якорь заметка', 'Альфа заметка']:
            item(title).wait_for(state='visible', timeout=30000)

        text_click('А-Я')
        wait(lambda: item_y('Альфа заметка') < item_y('Отредактированная заметка') < item_y('Якорь заметка'), 'алфавитная сортировка заметок')
        text_click('Создано')
        wait(lambda: item_y('Альфа заметка') < item_y('Якорь заметка') < item_y('Отредактированная заметка'), 'сортировка заметок по созданию')
        text_click('Изменено')
        wait(lambda: item_y('Отредактированная заметка') < item_y('Альфа заметка') < item_y('Якорь заметка'), 'сортировка заметок по изменению')
        text_click('Вручную')
        wait(lambda: item_y('Отредактированная заметка') < item_y('Якорь заметка') < item_y('Альфа заметка'), 'исходный Manual заметок')
        drag('Якорь заметка', 'Отредактированная заметка')
        wait(lambda: item_y('Якорь заметка') < item_y('Отредактированная заметка') < item_y('Альфа заметка'), 'ручная перестановка заметок')
        text_click('А-Я')
        wait(lambda: item_y('Альфа заметка') < item_y('Отредактированная заметка') < item_y('Якорь заметка'), 'выход из Manual заметок')
        text_click('Вручную')
        wait(lambda: item_y('Якорь заметка') < item_y('Отредактированная заметка') < item_y('Альфа заметка'), 'восстановление Manual заметок')

        snapshot = api('snapshot')
        first_project = next(p for p in snapshot['projects'] if p['title'] == 'Твой первый проект')
        note_manual = [n['title'] for n in sorted(
            [n for n in snapshot['notes'] if n['projectId'] == first_project['id']],
            key=lambda n: n['manualOrder'],
        )]
        assert note_manual[:3] == ['Якорь заметка', 'Отредактированная заметка', 'Альфа заметка'], note_manual

        # --- Перезапуск UI: manual order должен пережить reload ---
        page.reload(wait_until='networkidle')
        page.get_by_role('button', name='Главная', exact=True).wait_for(state='visible')
        tab('Проекты')
        wait(lambda: item_y('Альфа проект') < item_y('Твой первый проект') < item_y('Рабочие идеи'), 'Manual проектов после reload')
        role_click('button', re.compile(r'^Твой первый проект'))
        wait(lambda: item_y('Якорь заметка') < item_y('Отредактированная заметка') < item_y('Альфа заметка'), 'Manual заметок после reload')
        tab('Задачи')
        wait(lambda: item_y('Бета задача') < item_y('Альфа задача'), 'Manual задач после reload')

        assert not errors, errors
        page.screenshot(path=str(OUT / 'sorting-manual-after-reload.png'))
        (OUT / 'result.json').write_text(json.dumps({
            'passed': True,
            'checks': [
                '4 project sort modes',
                'project long-press reorder and Manual restore',
                '4 note sort modes',
                'note long-press reorder and Manual restore',
                'project/note/task Manual order survives UI reload',
            ],
            'pageErrors': errors,
        }, ensure_ascii=False, indent=2), encoding='utf-8')
        print('SORTING BROWSER PASSED')
    finally:
        with suppress(Exception):
            (OUT / 'semantics.txt').write_text(page.locator('body').aria_snapshot(), encoding='utf-8')
        browser.close()