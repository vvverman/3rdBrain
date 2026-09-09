"""Сборочный значок; на машине пользователя Python/Pillow не нужны."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import subprocess
root = Path(__file__).parent
iconset = root / '3rdBrain.iconset'
iconset.mkdir(exist_ok=True)
image = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((36, 36, 988, 988), radius=210, fill='#1d302a')
draw.rounded_rectangle((173, 160, 851, 830), radius=140, outline='#acd1bb', width=20)
font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf', 540)
draw.text((512, 500), '3', font=font, fill='#eff5f0', anchor='mm')
for size in (16, 32, 128, 256, 512):
    for scale in (1, 2):
        suffix = '@2x' if scale == 2 else ''
        image.resize((size * scale, size * scale), Image.Resampling.LANCZOS).save(iconset / f'icon_{size}x{size}{suffix}.png')
subprocess.run(['iconutil', '-c', 'icns', str(iconset), '-o', str(root / '3rdBrain.icns')], check=True)
