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

    def role_click(role, name):
        locator = page.get_by_role(role, name=name, exact=True)
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
        # Compose WASM держит семантический DOM поверх canvas, pointer получает canvas.
        page.mouse.click(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2)
        page.wait_for_timeout(250)

    def field(label, value):
        locator = page.get_by_role('textbox', name=label, exact=True)
        locator.wait_for(state='visible', timeout=30000)
        box = locator.bounding_box()
        assert box, label
        page.mouse.click(box['x'] + min(20, box['width'] / 2), box['y'] + min(20, box['height'] / 2))
        page.keyboard.press('Control+a')
        page.keyboard.insert_text(value)
        page.wait_for_timeout(350)

    def y(text):
        box = page.get_by_text(text, exact=True).bounding_box()
        assert box, text
        return box['y']

    def drag(source, target):
        a = page.get_by_text(source, exact=True).bounding_box()
        b = page.get_by_text(target, exact=True).bounding_box()
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
        role_click('button', re.compile('^' + re.escape(project_title)))
        button('Новая заметка')
        wait(lambda: current() is None, 'сохранение заметки ' + title)

    try:
        page.goto(BASE, wait_until='networkidle', timeout=60000)
        page.locator('canvas').first.wait_for(state='visible')
        page.get_by_role('button', name='Главная', exact=True).wait_for(state='visible')

        # --- Проекты: все четыре режима + ручная перестановка ---
        tab('Проекты')
        button('Новый проект')
        field('Название проекта', 'Альфа проект')
        field('Что сюда складывать', 'Проект для проверки сортировки')
        button('Сохранить')
        for title in ['Твой первый проект', 'Рабочие идеи', 'Альфа проект']:
            page.get_by_text(title, exact=True).wait_for(state='visible')

        text_click('А-Я')
        wait(lambda: y('Альфа проект') < y('Рабочие идеи') < y('Твой первый проект'), 'алфавитная сортировка проектов')
        text_click('Создано')
        wait(lambda: y('Альфа проект') < y('Рабочие идеи') < y('Твой первый проект'), 'сортировка проектов по созданию')
        text_click('Изменено')
        wait(lambda: y('Альфа проект') < y('Рабочие идеи') < y('Твой первый проект'), 'сортировка проектов по изменению')
        text_click('Вручную')
        wait(lambda: y('Твой первый проект') < y('Рабочие идеи') < y('Альфа проект'), 'исходный Manual проектов')
        drag('Альфа проект', 'Твой первый проект')
        wait(lambda: y('Альфа проект') < y('Твой первый проект') < y('Рабочие идеи'), 'ручная перестановка проектов')
        text_click('А-Я')
        wait(lambda: y('Альфа проект') < y('Рабочие идеи') < y('Твой первый проект'), 'выход из Manual проектов')
        text_click('Вручную')
        wait(lambda: y('Альфа проект') < y('Твой первый проект') < y('Рабочие идеи'), 'восстановление Manual проектов')

        snapshot = api('snapshot')
        project_manual = [p['title'] for p in sorted(snapshot['projects'], key=lambda p: p['manualOrder'])]
        assert project_manual[:3] == ['Альфа проект', 'Твой первый проект', 'Рабочие идеи'], project_manual

        # --- Заметки: создаём две дополнительные в одном проекте ---
        make_note('Якорь заметка', 'Якорь заметка для проверки ручного порядка.')
        make_note('Альфа заметка', 'Альфа заметка для проверки сортировки.')

        # В основном E2E старая заметка была закреплена. Для чистой проверки четырёх
        # sort-mode снимаем pin: вне Manual закрепление штатно имеет приоритет.
        # Затем меняем заметку последней: UPDATED обязан поднять именно её.
        tab('Проекты')
        role_click('button', re.compile('^Твой первый проект'))
        role_click('button', re.compile('^Отредактированная заметка'))
        button('Открепить')
        button('Править')
        field('Текст заметки', 'Текст обновлён последним специально для проверки даты изменения.')
        button('Сохранить')
        button('Назад')

        for title in ['Отредактированная заметка', 'Якорь заметка', 'Альфа заметка']:
            page.get_by_text(title, exact=True).wait_for(state='visible')

        text_click('А-Я')
        wait(lambda: y('Альфа заметка') < y('Отредактированная заметка') < y('Якорь заметка'), 'алфавитная сортировка заметок')
        text_click('Создано')
        wait(lambda: y('Альфа заметка') < y('Якорь заметка') < y('Отредактированная заметка'), 'сортировка заметок по созданию')
        text_click('Изменено')
        wait(lambda: y('Отредактированная заметка') < y('Альфа заметка') < y('Якорь заметка'), 'сортировка заметок по изменению')
        text_click('Вручную')
        wait(lambda: y('Отредактированная заметка') < y('Якорь заметка') < y('Альфа заметка'), 'исходный Manual заметок')
        drag('Якорь заметка', 'Отредактированная заметка')
        wait(lambda: y('Якорь заметка') < y('Отредактированная заметка') < y('Альфа заметка'), 'ручная перестановка заметок')
        text_click('А-Я')
        wait(lambda: y('Альфа заметка') < y('Отредактированная заметка') < y('Якорь заметка'), 'выход из Manual заметок')
        text_click('Вручную')
        wait(lambda: y('Якорь заметка') < y('Отредактированная заметка') < y('Альфа заметка'), 'восстановление Manual заметок')

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
        wait(lambda: y('Альфа проект') < y('Твой первый проект') < y('Рабочие идеи'), 'Manual проектов после reload')
        role_click('button', re.compile('^Твой первый проект'))
        wait(lambda: y('Якорь заметка') < y('Отредактированная заметка') < y('Альфа заметка'), 'Manual заметок после reload')
        tab('Задачи')
        wait(lambda: y('Бета задача') < y('Альфа задача'), 'Manual задач после reload')

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
