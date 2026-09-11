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
OUT=pathlib.Path('test-output/studio');OUT.mkdir(parents=True,exist_ok=True)

def api(path,data=None,method=None):
    headers={'X-Kasha-Client':'web'}
    if data is not None:headers['Content-Type']='application/json'
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
    def tab(name):click('button',name)
    def field(label,value):
        locator=page.get_by_role('textbox',name=label,exact=True);locator.wait_for(state='visible')
        box=locator.bounding_box();assert box,label
        page.mouse.click(box['x']+min(20,box['width']/2),box['y']+min(20,box['height']/2))
        page.keyboard.press('Control+a');page.keyboard.insert_text(value);page.wait_for_timeout(600)
    def current():return next((c for c in api('snapshot')['captures'] if c['noteId'] is None and c.get('taskId') is None),None)
    def ready():return wait(lambda:(c if (c:=current()) and c['status']=='READY' else None),'готовый пример')
    def player():
        nav=[]
        for name in ['Главная','Проекты','Задачи','Настройки']:
            locator=page.get_by_role('button',name=name,exact=True);locator.wait_for(state='visible');nav.append(locator.bounding_box())
        transport=[]
        for name in ['Запись','Воспроизвести','Пауза','Продолжить']:
            locator=page.get_by_role('button',name=name,exact=True)
            if locator.count():transport.append(locator.bounding_box())
        assert transport and all(b and b['y']+b['height']<=nav[0]['y']+2 for b in transport)
        assert nav[0]['y']>page.viewport_size['height']-100
    def screen(name):page.screenshot(path=str(OUT/(name+'.png')))
    try:
        page.goto(BASE,wait_until='networkidle',timeout=60000)
        page.locator('canvas').first.wait_for(state='visible')
        page.get_by_role('button',name='Запись',exact=True).wait_for(state='visible')
        player();assert page.locator('#webApp').bounding_box()['width']==430
        assert 'Входящие' not in page.locator('body').aria_snapshot()
        screen('home-light');checks.append('главная без входящих, постоянные панели')
        projects=api('snapshot')['projects'];assert len(projects)==1,projects
        work=projects[0];assert work['title']=='Твой первый проект' and work['instruction']==''
        page.reload(wait_until='networkidle')
        page.get_by_role('button',name='Запись',exact=True).wait_for(state='visible')
        assert api('snapshot')['projects']==projects
        checks.append('первый проект без настройки, повторный запуск без дубликатов')
        tab('Главная');button('Запись');wait(lambda:page.evaluate('kashaPlatform.phase()')=='recording','запись')
        wait(lambda:page.evaluate('kashaPlatform.level()')>0,'реальный аудиосигнал')
        assert page.get_by_role('button',name='Стоп',exact=True).count()==0
        page.get_by_role('button',name='Отправить',exact=True).wait_for(state='visible')
        screen('recording');tab('Проекты');player();assert page.evaluate('kashaPlatform.phase()')=='recording'
        tab('Настройки');player();assert page.evaluate('kashaPlatform.phase()')=='recording'
        assert 'Имитация ИИ' not in page.locator('body').aria_snapshot()
        tab('Главная');button('Пауза');assert page.evaluate('kashaPlatform.phase()')=='paused'
        assert page.evaluate('kashaPlatform.level()')==0
        button('Продолжить');wait(lambda:page.evaluate('kashaPlatform.phase()')=='recording','продолжение')
        page.wait_for_timeout(1000);button('Отправить');first=ready()
        checks.append('реальный микрофон Chrome, уровень, навигация, пауза и отправка')
        assert first['simulated'] and not first['llmApplied'] and first['audioFinalized'] and 'фигня' in first['transcript']
        assert first['savedSpeed']==1.5 and first['durationSeconds']>0
        page.get_by_role('button',name='Привести в порядок',exact=True).wait_for(state='visible');player()
        field('Название заметки','Проверка приложения');field('Текст заметки','В приложении какая-то фигня. Старый текст удалять нельзя.')
        button('Привести в порядок');wait(lambda:current()['llmApplied'],'ручное оформление')
        assert 'фигня' not in current()['preparedText'] and 'нельзя' in current()['preparedText']
        screen('note-light');checks.append('буквальный тестовый текст, ручная правка и оформление')
        button('Отправить в проект');player();page.get_by_role('button',name='Создать проект',exact=True).wait_for(state='visible');screen('choose-project');click('button',re.compile('^Твой первый проект'));button('Новая заметка')
        wait(lambda:current() is None,'новая заметка сохранена')
        note=api('snapshot')['notes'][0];old=note['body'];assert note['projectId']==work['id']
        checks.append('выбор проекта и новая заметка через UI')
        tab('Проекты');click('button',re.compile('^Твой первый проект'));click('button',re.compile('^Проверка приложения'))
        button('Закрепить');wait(lambda:api('snapshot')['notes'][0]['pinned'],'закрепление заметки')
        page.get_by_role('button',name='Открепить',exact=True).wait_for(state='visible')
        button('Править');field('Название заметки','Отредактированная заметка');field('Текст заметки','Текст исправлен после сохранения.')
        screen('note-editor');button('Сохранить')
        edited=wait(lambda:(n if (n:=api('snapshot')['notes'][0])['title']=='Отредактированная заметка' and n['body']=='Текст исправлен после сохранения.' else None),'редактирование заметки')
        assert edited['pinned'];old=edited['body'];checks.append('закрепление и встроенный редактор сохранённой заметки')
        # Сохранение оставляет пользователя в заметке; возвращаемся к списку и
        # снова открываем её — это отдельно проверяет отображение нового названия.
        button('Назад');click('button',re.compile('^Отредактированная заметка'))
        player();button('Воспроизвести');wait(lambda:page.evaluate('kashaPlatform.audioState().phase')=='playing','воспроизведение')
        button('Пауза');assert page.evaluate('kashaPlatform.audioState().phase')=='paused'
        button('Продолжить');button('Стоп');button('Назад');checks.append('плеер: воспроизведение, пауза, продолжение, стоп')
        tab('Главная');button('Запись');wait(lambda:page.evaluate('kashaPlatform.phase()')=='recording','вторая запись')
        tab('Проекты');click('button',re.compile('^Твой первый проект'));click('button',re.compile('^Отредактированная заметка'))
        # Заголовок заметки можно менять, но аудиоисточник остаётся отдельной сущностью
        # со своим исходным заголовком. Нажимаем именно его для запуска прослушивания.
        click('button',re.compile('^Проверка приложения'))
        page.get_by_role('button',name='Остановить и слушать',exact=True).wait_for(state='visible')
        player();button('Отмена');assert page.evaluate('kashaPlatform.phase()')=='recording'
        click('button',re.compile('^Проверка приложения'));button('Остановить и слушать')
        assert page.evaluate('kashaPlatform.phase()')=='idle';second=ready()
        if page.evaluate('kashaPlatform.audioState().phase')!='idle':button('Стоп')
        tab('Главная');page.get_by_role('button',name='Отправить в проект',exact=True).wait_for(state='visible')
        button('Отправить в проект');click('button',re.compile('^Твой первый проект'));click('button',re.compile('^Отредактированная заметка'))
        wait(lambda:current() is None,'дополнение');snapshot=api('snapshot')
        assert snapshot['notes'][0]['body'].startswith(old+'\n\n')
        assert snapshot['notes'][0]['pinned']
        assert len([c for c in snapshot['captures'] if c['noteId']==note['id']])==2
        checks.append('подтверждение конфликта, отмена без остановки, добавление второго источника')
        tab('Главная');button('Попробовать без микрофона');third=ready()
        button('Отправить в проект');button('Создать проект');player()
        field('Название проекта','Можно отменить');button('Назад')
        page.get_by_role('button',name='Создать проект',exact=True).wait_for(state='visible')
        assert current()['id']==third['id'] and len(api('snapshot')['projects'])==1
        button('Создать проект');field('Название проекта','Рабочие идеи')
        field('Что сюда складывать','Идеи интерфейсов');button('Сохранить')
        page.get_by_role('button',name='Новая заметка',exact=True).wait_for(state='visible');player()
        created=next(p for p in api('snapshot')['projects'] if p['title']=='Рабочие идеи')
        assert current()['id']==third['id'] and len(api('snapshot')['notes'])==1
        screen('new-project-destination');button('Новая заметка')
        wait(lambda:current() is None,'сохранение в только что созданный проект')
        assert any(n['projectId']==created['id'] for n in api('snapshot')['notes'])
        checks.append('создание проекта в выборе, возврат без потерь и сохранение в новый проект')
        tab('Главная');button('Попробовать без микрофона');ready();button('Отменить заметку');player();button('Удалить')
        wait(lambda:current() is None,'удаление текущего');checks.append('создание тестового сигнала и удаление')

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

        for language in ['ru','en','es','fr','de','uk','be','kk']:
            p=api('preferences');p.update(language=language,theme='dark' if language in ['uk','be','kk'] else 'light',autoRecord=False)
            api('preferences',p,'PUT');page.reload(wait_until='networkidle');page.wait_for_timeout(800)
            assert page.locator('canvas').first.is_visible();screen('locale-'+language)
        checks.append('восемь языков и обе темы')
        page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(500)
        assert page.locator('#webApp').bounding_box()['width']==390;screen('narrow-390')
        assert not errors,errors
        (OUT/'result.json').write_text(json.dumps({'passed':True,'checks':checks,'pageErrors':errors,'simulatedAI':True,'physicalMicrophone':False},ensure_ascii=False,indent=2),encoding='utf-8')
        print('STUDIO BROWSER PASSED: '+str(len(checks))+' групп проверок')
    finally:
        with suppress(Exception):(OUT/'semantics.txt').write_text(page.locator('body').aria_snapshot(),encoding='utf-8')
        with suppress(Exception):screen('last-screen')
        (OUT/'errors.json').write_text(json.dumps(errors,ensure_ascii=False),encoding='utf-8')
        browser.close()
