"""Только подготовка синтетического голоса для CI. Piper не входит в приложение."""
from pathlib import Path
import hashlib
import subprocess
import urllib.request
import wave
from piper import PiperVoice

OUT = Path('test-output/real-models'); OUT.mkdir(parents=True, exist_ok=True)
MODEL = OUT / 'ru_RU-irina-medium.onnx'
BASE = 'https://huggingface.co/rhasspy/piper-voices/resolve/a31bce3ed50c05399b2a830efd1c607df03cf4b5/ru/ru_RU/irina/medium/'
for name in (MODEL.name, MODEL.name + '.json'):
    with urllib.request.urlopen(BASE + name, timeout=120) as response:
        (OUT / name).write_bytes(response.read())
assert hashlib.sha256(MODEL.read_bytes()).hexdigest() == '8ff38212d23da300bbe3705c645e6e5b9475f0bfde01558eb17813e22acaaaaa'
voice = PiperVoice.load(str(MODEL))
phrases = ['В проекте приложения нужно исправить запись голоса.',
           'Добавить кнопку паузы и проверить сохранение заметок. Старый текст удалять нельзя.']
frames = []
for i, phrase in enumerate(phrases):
    src = OUT / f'phrase-{i}.wav'; dst = OUT / f'pcm-{i}.wav'
    with wave.open(str(src), 'wb') as wav: voice.synthesize_wav(phrase, wav)
    subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-i',str(src),'-ar','16000','-ac','1','-c:a','pcm_s16le',str(dst)], check=True)
    with wave.open(str(dst)) as wav: frames.append(wav.readframes(wav.getnframes()))
with wave.open(str(OUT / 'russian-with-pauses.wav'), 'wb') as wav:
    wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(16000)
    wav.writeframes(b'\0' * 64000 + frames[0] + b'\0' * 96000 + frames[1] + b'\0' * 64000)
# В CI-артефакт включается тестовая речь, а не чужие веса Piper.
MODEL.unlink(); (OUT / (MODEL.name + '.json')).unlink()
