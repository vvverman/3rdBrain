"""Монохромный знак звуковой волны для Dock. Не использует шрифты и готовые иконки."""
from pathlib import Path
from PIL import Image, ImageDraw
import subprocess

root=Path(__file__).resolve().parent
folder=root/'Kasha.iconset';folder.mkdir(exist_ok=True)
image=Image.new('RGBA',(1024,1024),(0,0,0,0));draw=ImageDraw.Draw(image)
draw.rounded_rectangle((30,30,994,994),radius=220,fill='#1C1B29')
heights=[90,160,270,420,610,490,330,200,100]
for i,height in enumerate(heights):
    x=224+i*72
    draw.rounded_rectangle((x-13,512-height/2,x+13,512+height/2),radius=13,fill='#F7F6FA')
for size in (16,32,128,256,512):
    image.resize((size,size),Image.Resampling.LANCZOS).save(folder/f'icon_{size}x{size}.png')
    image.resize((size*2,size*2),Image.Resampling.LANCZOS).save(folder/f'icon_{size}x{size}@2x.png')
subprocess.run(['iconutil','-c','icns',str(folder),'-o',str(root/'Kasha.icns')],check=True)
