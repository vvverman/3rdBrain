"""Настоящий Chrome, реальный runtime, синтетический микрофон. Веса моделей не используются."""
import json
import os
import pathlib
import shutil
import time
import urllib.request
from playwright.sync_api import sync_playwright

BASE = 'http://127.0.0.1:8787'
OUT = pathlib.Path('test-output'); OUT.mkdir(exist_ok=True)

def api(path, data=None):
    request = urllib.request.Request(BASE + '/api/' + path, headers={'X-3rdBrain-Client': 'web'})
    if data is not None:
        request.data = json.dumps(data).encode(); request.add_header('Content-Type', 'application/json')
        request.method = 'PUT' if path.endswith('/draft') else 'POST'
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)

for _ in range(90):
    try:
        api('health'); break
    except Exception: time.sleep(1)
else: raise AssertionError('Локальный сервис не запущен')

with sync_playwright() as p:
    executable = os.getenv('CHROME_PATH') or shutil.which('google-chrome') or shutil.which('chromium')
    browser = p.chromium.launch(executable_path=executable, headless=True, args=[
        '--no-sandbox', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream',
        '--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
    context = browser.new_context(viewport={'width':1440,'height':900}, locale='ru-RU')
    page = context.new_page(); errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    try:
        page.goto(BASE, wait_until='networkidle', timeout=60000)
        page.locator('canvas').first.wait_for(state='visible', timeout=30000)
        page.get_by_role('button', name='Пока без записи', exact=True).click(timeout=30000)
        assert page.locator('#webApp').bounding_box()['width'] == 430
        page.screenshot(path=str(OUT/'desktop-mobile-layout.png'))
        # Проверяем настоящий browser MediaRecorder с синтетическим микрофоном Chrome.
        page.get_by_role('button', name='Записать', exact=True).click()
        page.wait_for_function('thirdBrainPlatform.phase() === "recording"')
        page.wait_for_timeout(1600)
        page.get_by_role('button', name='Пауза', exact=True).click()
        page.wait_for_function('thirdBrainPlatform.phase() === "paused"')
        page.get_by_role('button', name='Продолжить', exact=True).click()
        page.wait_for_function('thirdBrainPlatform.phase() === "recording"')
        page.route('**/api/captures/audio', lambda route: route.abort())
        page.get_by_role('button', name='Готово', exact=True).click()
        page.wait_for_function('thirdBrainPlatform.phase() === "idle"')
        page.wait_for_function('thirdBrainPlatform.pending()')
        page.get_by_role('button', name='Понятно', exact=True).click()
        page.unroute('**/api/captures/audio')
        page.reload(wait_until='networkidle')
        page.get_by_role('button', name='Повторить отправку', exact=True).click(timeout=30000)
        page.wait_for_function('thirdBrainPlatform.pending().then(v => !v)')
        capture = api('snapshot')['captures'][0]
        for _ in range(30):
            capture = api('snapshot')['captures'][0]
            if capture['status'] == 'NEEDS_MODEL': break
            time.sleep(.2)
        assert capture['status'] == 'NEEDS_MODEL' and not capture['transcript']
        with urllib.request.urlopen(BASE + '/api/captures/' + capture['id'] + '/audio') as response:
            assert len(response.read()) > 100
        page.screenshot(path=str(OUT/'recording-source.png'))
        # Транспорт использует те же общие Kotlin-правила, что будущие платформы.
        project = api('projects', {'title':'Тестовый проект','instruction':'Мысли про интерфейс'})
        api('captures/' + capture['id'] + '/draft', {'title':'Первая мысль','text':'Старый текст  '})
        note = api('captures/' + capture['id'] + '/distribute', {'projectId':project['id']})
        repeated = api('captures/' + capture['id'] + '/distribute', {'projectId':project['id']})
        assert note['id'] == repeated['id'] and note['body'] == 'Старый текст  '
        # На узком окне — те же элементы; никакой desktop-разметки.
        page.set_viewport_size({'width':390,'height':844})
        page.wait_for_timeout(800)
        assert page.locator('#webApp').bounding_box()['width'] == 390
        page.screenshot(path=str(OUT/'mobile-layout.png'))
        assert not errors, errors
        (OUT/'browser-result.json').write_text(json.dumps({'passed':True,'checks':[
            'render-430-and-390','record-pause-resume','failed-upload-keeps-audio',
            'reload-and-recover','saved-audio-download','missing-model-honesty',
            'project-and-idempotent-distribution'], 'pageErrors':errors}, ensure_ascii=False, indent=2))
        print('BROWSER SMOKE PASSED: 7 проверок, без настоящих весов моделей')
    finally:
        (OUT/'browser.html').write_text(page.content())
        (OUT/'page-errors.json').write_text(json.dumps(errors, ensure_ascii=False))
        page.screenshot(path=str(OUT/'last-screen.png'))
        browser.close()
