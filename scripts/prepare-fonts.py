"""Сборка ресурсов Kasha: закреплённый OFL-шрифт Wix Madefor и проверка всех языков интерфейса."""
import hashlib
import io
from pathlib import Path
from urllib.request import urlopen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
GOOGLE_FONTS_COMMIT = '8e44913e4ff26fc997e6856c1ec40ff4791c98c5'
BASE = f'https://raw.githubusercontent.com/google/fonts/{GOOGLE_FONTS_COMMIT}/ofl/'
resources = ROOT / 'composeApp/src/commonMain/composeResources'
(resources / 'font').mkdir(parents=True, exist_ok=True)
(resources / 'files/licenses').mkdir(parents=True, exist_ok=True)

required = set('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюяІіЇїЄєҐґЎўӘәҒғҚқҢңӨөҰұҮүҺһÑñÁáÉéÍíÓóÚúÜüÇçÀàÂâÊêËëÎîÏïÔôÙùÛûŸÿŒœÄäÖöß')

def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def load(url: str, expected: str) -> bytes:
    with urlopen(url, timeout=60) as response:
        data = response.read()
    assert blob_sha(data) == expected, f'Unexpected font blob for {url}'
    return data

def verify(data: bytes, name: str) -> None:
    font = TTFont(io.BytesIO(data))
    missing = sorted(ord(char) for char in required if ord(char) not in font.getBestCmap())
    assert not missing, f'{name} lacks required glyphs: {missing}'

text_files = {
    'wix_madefor_text_regular.ttf': ('WixMadeforText-Regular.ttf', 'a3e2b40028cc2abfd0cc4323540a9c68f8bb91e2'),
    'wix_madefor_text_medium.ttf': ('WixMadeforText-Medium.ttf', '33bcae00135309a5b2d34f6b750605168d73f1db'),
    'wix_madefor_text_semibold.ttf': ('WixMadeforText-SemiBold.ttf', '59587a8aaa8a2a11018b8dfaf3a5f3da18b603c8'),
}
for target, (source, sha) in text_files.items():
    data = load(BASE + 'wixmadefortext/' + source, sha)
    verify(data, source)
    (resources / 'font' / target).write_bytes(data)

# Display используется только для крупных заголовков/брендинга; 600 сохраняет спокойный Apple-like характер.
display_variable = load(BASE + 'wixmadefordisplay/WixMadeforDisplay%5Bwght%5D.ttf', '92c56bef536e11285f052fa6b55229db5c09b5bb')
verify(display_variable, 'Wix Madefor Display')
display = instantiateVariableFont(TTFont(io.BytesIO(display_variable)), {'wght': 600}, inplace=True)
display.save(resources / 'font/wix_madefor_display_semibold.ttf')

license_data = load(BASE + 'wixmadefortext/OFL.txt', 'b7dfd2a1ca20e15ce1b1d5ec7166545730e3b1ce')
(resources / 'files/licenses/Wix-Madefor-OFL.txt').write_bytes(license_data)
print('Wix Madefor: Text 400/500/600 + Display 600; Cyrillic and all 8 Kasha alphabets verified')
