"""Только сборка: закреплённый OFL-шрифт, статические начертания и проверка восьми алфавитов."""
import hashlib
import io
from pathlib import Path
from urllib.request import urlopen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
BASE = 'https://raw.githubusercontent.com/google/fonts/142c8963e7606b510c93a644c82a4c4cdeae6ef9/ofl/onest/'
resources = ROOT / 'composeApp/src/commonMain/composeResources'
(resources / 'font').mkdir(parents=True, exist_ok=True)
(resources / 'files/licenses').mkdir(parents=True, exist_ok=True)
with urlopen(BASE + 'Onest%5Bwght%5D.ttf', timeout=60) as response:
    data = response.read()
assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == '477cb31027450d80e486a53119641bf87dde3d4c'
font = TTFont(io.BytesIO(data))
required = set('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюяІіЇїЄєҐґЎўӘәҒғҚқҢңӨөҰұҮүҺһÑñÁáÉéÍíÓóÚúÜüÇçÀàÂâÊêËëÎîÏïÔôÙùÛûŸÿŒœÄäÖöß')
missing = sorted(ord(char) for char in required if ord(char) not in font.getBestCmap())
assert not missing, f'Onest lacks required glyphs: {missing}'
for name, weight in [('regular', 400), ('medium', 500), ('semibold', 600)]:
    instance = instantiateVariableFont(TTFont(io.BytesIO(data)), {'wght': weight}, inplace=True)
    instance.save(resources / f'font/onest_{name}.ttf')
with urlopen(BASE + 'OFL.txt', timeout=60) as response:
    license_data = response.read()
assert hashlib.sha1(b'blob ' + str(len(license_data)).encode() + b'\0' + license_data).hexdigest() == 'b6f4f91487900bb8f00ae991724121f1d59e30d9'
(resources / 'files/licenses/Onest-OFL.txt').write_bytes(license_data)
print('Onest: 3 начертания, все 8 алфавитов проверены')
