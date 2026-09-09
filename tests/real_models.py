"""Настоящие веса + русская синтезированная речь + настоящий HTTP/диск. Не тест качества живого микрофона."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid
import wave

BASE = 'http://127.0.0.1:8787/api/'
OUT = Path('test-output/real-models'); OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'result.json').unlink(missing_ok=True)

def api(path, data=None, method=None):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, body, method=method, headers={
        'X-3rdBrain-Client': 'web', 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)

def capture(cid):
    return next(c for c in api('snapshot')['captures'] if c['id'] == cid)

def processed(cid):
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        c = capture(cid)
        if c['status'] not in ('QUEUED','TRANSCRIBING','COMPACTING','POLISHING','RECORDING'):
            (OUT / (cid + '.json')).write_text(json.dumps(c, ensure_ascii=False, indent=2), encoding='utf-8')
            return c
        time.sleep(1)
    raise AssertionError('Истекло время обработки настоящими моделями')

def upload(path):
    cid = str(uuid.uuid4()); boundary = 'brain-' + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="audio"; filename="voice.wav"\r\n'
            'Content-Type: audio/wav\r\n\r\n').encode() + path.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(BASE + 'captures/audio', body, headers={
        'X-3rdBrain-Client':'web', 'X-Capture-Id':cid, 'Content-Type':'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=30) as response:
        assert json.load(response)['id'] == cid
    return cid

for _ in range(90):
    try: api('health'); break
    except Exception: time.sleep(1)
else: raise AssertionError('Сервис не запустился')

assert api('snapshot')['runtime']['whisperConfigured']
assert api('snapshot')['runtime']['llmConfigured']
projects = [
    api('projects', {'title':'Разработка приложения','instruction':'Идеи про интерфейс, запись голоса, кнопки и сохранение заметок.'}),
    api('projects', {'title':'Кулинария','instruction':'Только рецепты, приготовление еды и продукты. Не разработка программ.'}),
    api('projects', {'title':'Закреплённый','instruction':'Личные мысли'}),
]
api('projects/' + projects[2]['id'] + '/pin', {'pinned':True})
phrases = ['В проекте приложения нужно исправить запись голоса.',
           'Добавить кнопку паузы и проверить сохранение заметок. Старый текст удалять нельзя.']
frames = []
for i, phrase in enumerate(phrases):
    src = OUT / f'phrase-{i}.wav'; dst = OUT / f'pcm-{i}.wav'
    subprocess.run(['espeak-ng','-v','ru','-s','140','-w',str(src),phrase], check=True)
    subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-i',str(src),'-ar','16000','-ac','1','-c:a','pcm_s16le',str(dst)], check=True)
    with wave.open(str(dst)) as wav: frames.append(wav.readframes(wav.getnframes()))
voice = OUT / 'russian-with-pauses.wav'
with wave.open(str(voice), 'wb') as wav:
    wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(16000)
    wav.writeframes(b'\0' * 64000 + frames[0] + b'\0' * 96000 + frames[1] + b'\0' * 64000)
started = time.monotonic()
cid = upload(voice)
c = processed(cid)
print(json.dumps(c, ensure_ascii=False, indent=2), flush=True)
assert c['status'] == 'READY', c['message']
assert c['llmApplied'], c['message']
assert c['rankingApplied'], c['message']
for stem in ('запис','пауз','замет'):
    assert stem in c['transcript'].lower(), c['transcript']
    assert stem in c['preparedText'].lower(), c['preparedText']
assert 'нельзя' in c['transcript'].lower() and 'нельзя' in c['preparedText'].lower()
assert c['relevance'][projects[0]['id']] > c['relevance'][projects[1]['id']], c['relevance']
assert c['compactAudioFileName'] and c['compactDurationSeconds'] < c['durationSeconds'] - 2
with urllib.request.urlopen(BASE + 'captures/' + cid + '/audio') as response:
    original = response.read()
assert hashlib.sha256(original).digest() == hashlib.sha256(voice.read_bytes()).digest()
with urllib.request.urlopen(BASE + 'captures/' + cid + '/audio?compact=true') as response:
    compact = OUT / 'compact.m4a'; compact.write_bytes(response.read())
subprocess.run(['ffmpeg','-v','error','-i',str(compact),'-f','null','-'],check=True)
note = api('captures/' + cid + '/distribute', {'projectId':projects[0]['id']})
assert note == api('captures/' + cid + '/distribute', {'projectId':projects[0]['id']})
result = {'passed':True, 'speech':'espeak-ng ru, не живая речь', 'whisper':'small',
          'llm':'Qwen2.5-1.5B-Instruct Q4_K_M','elapsedSeconds':round(time.monotonic()-started,2),
          'transcript':c['transcript'],'preparedText':c['preparedText'],'title':c['title'],
          'relevance':c['relevance'],'durationSeconds':c['durationSeconds'],
          'compactDurationSeconds':c['compactDurationSeconds'],'originalSha256':hashlib.sha256(original).hexdigest()}
(OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('REAL MODELS PASSED', flush=True)
