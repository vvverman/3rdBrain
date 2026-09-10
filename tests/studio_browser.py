"""Новый общий UI: реальные клики, редактирование, микрофон Chrome, аудио и явный имитатор ИИ."""
import json
import os
import pathlib
import re
import shutil
import time
import urllib.request
from contextlib import suppress
from playwright.sync_api import sync_playwright

BASE='http://127.0.0.1:8787'
OUT=pathlib.Path('test-output/studio'); OUT.mkdir(parents=True,exist_ok=True)

def api(path,data=None,method=None):
    headers={'X-3rdBrain-Client':'web'}
    if data is not None: headers['Content-Type']='application/json'
    req=urllib.request.Request(BASE+'/api/'+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)

for _ in range(90):
    try:api('health');break
    except Exception:time.sleep(1)
else:raise AssertionError('Сервис не запустился')

api('preferences',{'autoRecord':False,'language':'ru','theme':'light'},'PUT')
with sync_playwright() as pw:
    executable=os.getenv('CHROME_PATH') or shutil.which('google-chrome') or shutil.which('chromium')
    browser=pw.chromium.launch(executable_path=executable,headless=True,args=['--no-sandbox','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
    context=browser.new_context(viewport={'width':1280,'height':1000},locale='ru-RU')
    page=context.new_page();errors=[];checks=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    def wait(check,description,seconds=30):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            value=check()
            if value:return value
            page.wait_for_timeout(100)
        raise AssertionError('Не дождались: '+description)
    def click(role,name):
        locator=page.get_by_role(role,name=name,exact=True)
        locator.wait_for(state='visible',timeout=30000)
        box=locator.bounding_box();assert box and box['height']>0,name
        assert box['y']>=0 and box['y']+box['height']<=page.viewport_size['height']+3,(name,box)
        page.mouse.click(box['x']+box['width']/2,box['y']+box['height']/2)
        page.wait_for_timeout(200)
    def button(name):click('button',name)
    def tab(name):click('tab',name)
    def field(label,value):
        box=page.get_by_role('textbox',name=label,exact=True).bounding_box()
        assert box,label
        page.mouse.click(box['x']+min(20,box['width']/2),box['y']+min(20,box['height']/2))
        page.keyboard.press('Control+a');page.keyboard.insert_text(value)
        page.wait_for_timeout(600)
    def current():return next((c for c in api('snapshot')['captures'] if c['noteId'] is None),None)
    def ready():return wait(lambda:(c if (c:=current()) and c['status']=='READY' else None),'готовый пример')
    def player():
        box=page.get_by_label('global-player',exact=True).bounding_box()
        assert box and 0<=box['y']<page.viewport_size['height'],'Плеер скрыт'
        for name in ['Главная','Проекты','Настройки']:page.get_by_role('tab',name=name,exact=True).wait_for(state='visible')
    def screen(name):page.screenshot(path=str(OUT/(name+'.png')))
    try:
        page.goto(BASE,wait_until='networkidle',timeout=60000)
        page.locator('canvas').first.wait_for(state='visible')
        page.get_by_role('button',name='Запись',exact=True).wait_for(state='visible')
        player();assert page.locator('#webApp').bounding_box()['width']==430
        assert 'Входящие' not in page.locator('body').aria_snapshot()
        screen('home-light');checks.append('главная без входящих, постоянные панели')
        tab('Проекты');button('Новый проект')
        field('Название проекта','Приложение');field('Что сюда складывать','Запись голоса и сохранение заметок')
        button('Сохранить')
        work=wait(lambda:next((p for p in api('snapshot')['projects'] if p['title']=='Приложение'),None),'проект создан через UI')
        checks.append('создание проекта через интерфейс')
        tab('Главная');button('Запись')
        wait(lambda:page.evaluate('thirdBrainPlatform.phase()')=='recording','запись')
        wait(lambda:page.evaluate('thirdBrainPlatform.level()')>0,'реальный аудиосигнал')
        screen('recording');tab('Проекты');player();assert page.evaluate('thirdBrainPlatform.phase()')=='recording'
        tab('Настройки');player();assert page.evaluate('thirdBrainPlatform.phase()')=='recording'
        tab('Главная');button('Пауза');assert page.evaluate('thirdBrainPlatform.phase()')=='paused'
        assert page.evaluate('thirdBrainPlatform.level()')==0
        button('Продолжить');wait(lambda:page.evaluate('thirdBrainPlatform.phase()')=='recording','продолжение')
        page.wait_for_timeout(1000);button('Стоп');first=ready()
        checks.append('реальный микрофон, уровень, навигация, пауза и стоп')
        assert first['simulated'] and not first['llmApplied'] and first['audioFinalized'] and 'фигня' in first['transcript']
        assert first['savedSpeed']==1.5 and first['durationSeconds']>0
        page.get_by_role('button',name='Привести в порядок',exact=True).wait_for(state='visible');player()
        field('Название заметки','Проверка приложения')
        field('Текст заметки','В приложении какая-то фигня. Старый текст удалять нельзя.')
        button('Привести в порядок')
        wait(lambda:current()['llmApplied'],'ручное оформление')
        assert 'фигня' not in current()['preparedText'] and 'нельзя' in current()['preparedText']
        screen('note-light');checks.append('буквальный тестовый текст, ручная правка и оформление')
        button('Отправить в проект');player()
        # Имя кнопки проекта включает инструкцию: семантика Compose объединяет потомков.
        click('button',re.compile('^Приложение'))
        button('Новая заметка');wait(lambda:current() is None,'новая заметка сохранена')
        note=api('snapshot')['notes'][0];old=note['body'];source=api('snapshot')['captures'][0]
        assert note['projectId']==work['id'];checks.append('выбор проекта и новая заметка через UI')
        tab('Проекты');click('button',re.compile('^Приложение'));click('button',re.compile('^Проверка приложения'))
        player();button('Воспроизвести');wait(lambda:page.evaluate('thirdBrainPlatform.audioState().phase')=='playing','воспроизведение')
        button('Пауза');assert page.evaluate('thirdBrainPlatform.audioState().phase')=='paused'
        button('Продолжить');button('Стоп')
        checks.append('плеер: воспроизведение, пауза, продолжение, стоп')
        tab('Главная');button('Запись');wait(lambda:page.evaluate('thirdBrainPlatform.phase()')=='recording','вторая запись')
        tab('Проекты')
        # Открытая заметка сохранилась при переключении вкладки. Выбор источника требует подтверждения.
        click('button',re.compile('^Проверка приложения'))
        page.get_by_role('button',name='Остановить и слушать',exact=True).wait_for(state='visible')
        player();button('Отмена');assert page.evaluate('thirdBrainPlatform.phase()')=='recording'
        click('button',re.compile('^Проверка приложения'));button('Остановить и слушать')
        assert page.evaluate('thirdBrainPlatform.phase()')=='idle'
        second=ready();button('Стоп');tab('Главная');wait(lambda:current()['status']=='READY','обработка второй записи')
        button('Отправить в проект');click('button',re.compile('^Приложение'));click('button',re.compile('^Проверка приложения'))
        wait(lambda:current() is None,'дополнение')
        snapshot=api('snapshot');assert snapshot['notes'][0]['body'].startswith(old+'\n\n')
        assert len([c for c in snapshot['captures'] if c['noteId']==note['id']])==2
        checks.append('подтверждение конфликта, отмена без остановки, добавление второго источника')
        # Явная демонстрация без микрофона.
        button('Попробовать без микрофона');third=ready();button('Отменить заметку');player();button('Удалить')
        wait(lambda:current() is None,'удаление текущего');checks.append('создание тестового сигнала и удаление')
        # Реальное автоопределение локали/темы в общей UI. Нет смешения языков в рамках экрана.
        for language in ['ru','en','es','fr','de','uk','be','kk']:
            p=api('preferences');p.update(language=language,theme='dark' if language in ['uk','be','kk'] else 'light',autoRecord=False)
            api('preferences',p,'PUT');page.reload(wait_until='networkidle');page.wait_for_timeout(800)
            assert page.locator('canvas').first.is_visible();screen('locale-'+language)
        checks.append('восемь языков и обе темы')
        page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(500);assert page.locator('#webApp').bounding_box()['width']==390;screen('narrow-390')
        assert not errors,errors
        (OUT/'result.json').write_text(json.dumps({'passed':True,'checks':checks,'pageErrors':errors,'simulatedAI':True,'physicalMicrophone':False},ensure_ascii=False,indent=2),encoding='utf-8')
        print('STUDIO BROWSER PASSED: '+str(len(checks))+' групп проверок')
    finally:
        with suppress(Exception):(OUT/'semantics.txt').write_text(page.locator('body').aria_snapshot(),encoding='utf-8')
        with suppress(Exception):screen('last-screen')
        (OUT/'errors.json').write_text(json.dumps(errors,ensure_ascii=False),encoding='utf-8')
        browser.close()
