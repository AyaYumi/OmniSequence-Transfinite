"""Generate tiled, looped pixel-art liquid for Singularity Sequence Matter.

Art direction: deep indigo liquid, curling violet/cyan luminous filaments,
small bright grains; still surface ripples and downward flowing variant.
Every spatial/time frequency is periodic for seamless tiles and loops.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/molecularmanipulator/textures/block"
PREVIEW = ROOT / "build/reports/singularity-fluid"
FRAMES = 32
TAU = 2 * np.pi


def texture(size, frame, flowing=False):
    y, x = np.mgrid[0:size, 0:size].astype(float) / size
    t = frame / FRAMES * TAU
    # Integral periods preserve matching edges. Flowing textures drift down.
    if flowing:
        y = y - t / TAU
    wx = x + .065 * np.sin(TAU * (y * 2 + x) + t) + .035 * np.cos(TAU * y * 3 - t)
    wy = y + .06 * np.sin(TAU * (x * 2 - y) - t) + .04 * np.cos(TAU * x * 3 + t)
    wave = np.sin(TAU * (2 * wx + 3 * wy) + .45 * np.sin(TAU * (3 * wx - wy) - t))
    wave += .55 * np.sin(TAU * (4 * wx - 2 * wy) + t)
    wave += .25 * np.cos(TAU * (wx - 5 * wy) - 2 * t)
    body = (np.sin(TAU * (wx - 2 * wy) - t) + 1) / 2
    violet = np.exp(-((wave - .1) / .37) ** 2)
    filament = np.exp(-((wave + .38) / .16) ** 2)
    cyan = (np.sin(TAU * (2 * wx + wy) + t) + 1) / 2
    grains = np.maximum(0, np.sin(TAU * (9 * x + 11 * y) + t) * np.cos(TAU * (13 * x - 7 * y) - t)) ** 14
    r = 12 + 27 * body + 66 * violet + 95 * filament * (1-cyan) + 115 * grains
    g = 12 + 13 * body + 25 * violet + 128 * filament * cyan + 127 * grains
    b = 36 + 36 * body + 107 * violet + 90 * filament + 100 * grains
    rgb = np.clip(np.stack([r,g,b],axis=-1),0,255)
    # A constrained 6-bit palette keeps the native pixels crisp.
    rgb = (np.rint(rgb / 4) * 4).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, np.full((size,size),255,dtype=np.uint8)]))


def generate():
    TEXTURES.mkdir(parents=True,exist_ok=True)
    PREVIEW.mkdir(parents=True,exist_ok=True)
    for name,size,flowing in [('singularity_sequence_matter_still',32,False),('singularity_sequence_matter_flow',64,True)]:
        frames=[texture(size,i,flowing) for i in range(FRAMES)]
        strip=Image.new('RGBA',(size,size*FRAMES))
        for i,image in enumerate(frames): strip.paste(image,(0,i*size))
        strip.save(TEXTURES/f'{name}.png')
        (TEXTURES/f'{name}.png.mcmeta').write_text(json.dumps({'animation':{'frametime':2,'interpolate':True,'width':size,'height':size}},indent=2)+'\n',encoding='utf-8')
        preview=[image.resize((256,256),Image.Resampling.NEAREST).convert('RGB') for image in frames]
        preview[0].save(PREVIEW/f'{name}.gif',save_all=True,append_images=preview[1:],duration=100,loop=0,disposal=2)
        contact=Image.new('RGB',(256*4,256*2),(10,10,20))
        for i in range(8):contact.paste(preview[i*4],((i%4)*256,(i//4)*256))
        contact.save(PREVIEW/f'{name}_frames.png')
        assert len({image.tobytes() for image in frames})==FRAMES
        print(f'{name}: {size}x{size}, {FRAMES} unique frames, 3.2-second interpolated loop')

if __name__=='__main__':generate()
