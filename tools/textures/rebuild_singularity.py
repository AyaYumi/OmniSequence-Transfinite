"""Rebuild native emissive overlays from edited Singularity textures without repainting them."""
import argparse
import copy
import json
import math
from pathlib import Path
import shutil
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/molecularmanipulator'
TEX = ASSETS / 'textures/block/singularity'
NAMES = (
    'collector', 'conduit', 'conduit_end', 'controller', 'core_seal', 'crystal',
    'gilded', 'glass', 'jade', 'pillar', 'pillar_end', 'ring_side',
    'ring_top', 'socket', 'stabilizer',
)
ANIMATIONS = {
    'collector': 'gather', 'conduit': 'rise', 'conduit_end': 'gather',
    'controller': 'orbit', 'core_seal': 'orbit', 'crystal': 'rise',
    'ring_side': 'flow', 'ring_top': 'flow',
    'socket': 'pulse', 'stabilizer': 'pulse',
}
EMISSION = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
FRAME_COUNT = 24


def blue(pixel):
    r, g, b, a = pixel
    return a >= 128 and g >= 165 and g - r >= 28 and b - r >= 35


def central_recess(image):
    """Keep generated inscriptions inside the authored connected central dark panel."""
    pending = [(7, 7), (8, 7), (7, 8), (8, 8)]
    found = set()
    while pending:
        x, y = pending.pop()
        if (x, y) in found or not (2 <= x < 14 and 2 <= y < 14):
            continue
        r, g, b, a = image.getpixel((x, y))
        if a < 128 or r >= 75 or g >= 105 or b >= 125:
            continue
        found.add((x, y))
        pending.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    return found


def mask_for(name, image):
    pixels = set()
    if name == 'crystal':
        # Thin highlights, rather than making every face of the crystal white.
        pixels = {(x, y) for y in range(16) for x in range(16)
                  if blue(image.getpixel((x, y))) and image.getpixel((x, y))[0] >= 170}
    elif name == 'conduit':
        pixels = {(x, y) for x in (6, 9) for y in range(16)
                  if image.getpixel((x, y))[0] < 75}
    else:
        pixels = {(x, y) for y in range(16) for x in range(16) if blue(image.getpixel((x, y)))}
        if not pixels:
            recess = central_recess(image)
            if name == 'controller':
                pixels = {(x, y) for x, y in recess if x in (7, 8) or y in (7, 8)}
            else:
                pixels = {(x, y) for x, y in recess
                          if any((x + dx, y + dy) not in recess for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))}
    if not pixels:
        raise ValueError(f'No authored blue area or central recess found for {name}')
    return pixels


def frame_for(name, image, pixels, frame):
    output = Image.new('RGBA', (16, 16))
    mode = ANIMATIONS[name]
    for x, y in pixels:
        source = image.getpixel((x, y))
        if mode == 'rise':
            offset = y / 16
        elif mode == 'flow':
            offset = -x / 16
        elif mode == 'orbit':
            offset = math.atan2(y - 7.5, x - 7.5) / math.tau
        elif mode == 'gather':
            offset = -math.hypot(x - 7.5, y - 7.5) / 8
        else:
            offset = (x + y) / 96
        wave = (0.5 + 0.5 * math.cos(math.tau * (frame / FRAME_COUNT + offset))) ** 2
        base = source[:3] if blue(source) else (147, 197, 219)
        trough = tuple(round(c * 0.52) for c in base)
        peak = (213, 244, 250)
        color = tuple(round(low + (high - low) * wave) for low, high in zip(trough, peak))
        output.putpixel((x, y), (*color, source[3]))
    return output


def native_uv(part, face):
    x0, y0, z0 = part['from']
    x1, y1, z1 = part['to']
    return {
        'down': [x0, 16 - z1, x1, 16 - z0],
        'up': [x0, z0, x1, z1],
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        'south': [x0, 16 - y1, x1, 16 - y0],
        'west': [z0, 16 - y1, z1, 16 - y0],
        'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def update_models():
    changed = []
    for path in sorted((ASSETS / 'models/block').glob('singularity_*.json')):
        original = json.loads(path.read_text(encoding='utf-8'))
        if 'elements' not in original:
            continue
        model = copy.deepcopy(original)
        textures = model['textures']
        parts = [part for part in model['elements']
                 if not any(face['texture'].endswith('_glow') for face in part['faces'].values())]
        for key in tuple(textures):
            if key.endswith('_glow'):
                del textures[key]
        overlays = []
        for part in parts:
            glow_faces = {}
            for direction, face in part['faces'].items():
                key = face['texture'].removeprefix('#')
                surface = textures.get(key, '').rsplit('/', 1)[-1]
                if surface not in ANIMATIONS:
                    continue
                textures[key + '_glow'] = textures[key] + '_glow'
                glow_face = copy.deepcopy(face)
                glow_face['texture'] = '#' + key + '_glow'
                # Preserve the source UV crop, including smaller crystal/model parts.
                glow_face['uv'] = copy.deepcopy(face.get('uv', native_uv(part, direction)))
                glow_faces[direction] = glow_face
            if glow_faces:
                glow = copy.deepcopy(part)
                glow['from'] = [round(value - .005, 3) for value in part['from']]
                glow['to'] = [round(value + .005, 3) for value in part['to']]
                glow['faces'] = glow_faces
                glow['shade'] = False
                glow['forge_data'] = EMISSION.copy()
                overlays.append(glow)
        model['elements'] = parts + overlays
        if model != original:
            path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            changed.append(path.name)
    return changed


def previews(images, frames, output):
    output.mkdir(parents=True, exist_ok=True)
    try:
        font = ImageFont.truetype('DejaVuSansMono.ttf', 20)
    except OSError:
        font = ImageFont.load_default(size=20)
    animated = list(ANIMATIONS)
    captures = []
    for frame in range(FRAME_COUNT):
        page = Image.new('RGB', (1200, 900), (17, 24, 34))
        draw = ImageDraw.Draw(page)
        for index, name in enumerate(animated):
            x, y = index % 4 * 300 + 16, index // 4 * 300 + 16
            base = images[name]
            day = Image.alpha_composite(base, frames[name][frame]).resize((112, 112), Image.Resampling.NEAREST)
            night_base = base.copy()
            night_base.putdata([(*(round(c * .14) for c in pixel[:3]), pixel[3]) for pixel in base.getdata()])
            night = Image.alpha_composite(night_base, frames[name][frame]).resize((112, 112), Image.Resampling.NEAREST)
            page.paste(day, (x, y), day)
            page.paste(night, (x + 136, y), night)
            draw.text((x, y + 136), name, font=font, fill=(225, 237, 240))
            draw.text((x, y + 170), 'day        night', font=font, fill=(148, 174, 190))
        captures.append(page)
    captures[0].save(output / 'singularity-day-night.png')
    captures[0].save(output / 'singularity-animation.gif', save_all=True, append_images=captures[1:],
                     duration=100, loop=0, disposal=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=TEX, help='Directory with 16 edited static PNGs')
    parser.add_argument('--preview', type=Path, default=ROOT / 'build/singularity-user-textures')
    args = parser.parse_args()
    images = {}
    for name in NAMES:
        path = args.source / (name + '.png')
        with Image.open(path) as original:
            if original.size != (16, 16):
                raise ValueError(f'{path}: expected native 16x16, got {original.size}')
            images[name] = original.convert('RGBA')
    masks = {name: mask_for(name, images[name]) for name in ANIMATIONS}
    frames = {name: [frame_for(name, images[name], masks[name], frame) for frame in range(FRAME_COUNT)]
              for name in ANIMATIONS}
    for name, sequence in frames.items():
        assert len({image.tobytes() for image in sequence}) > 8, name
        assert len({image.getchannel('A').tobytes() for image in sequence}) == 1, name
    TEX.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        source, target = args.source / (name + '.png'), TEX / (name + '.png')
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        assert Image.open(target).convert('RGBA').tobytes() == images[name].tobytes()
    for name, sequence in frames.items():
        strip = Image.new('RGBA', (16, 16 * FRAME_COUNT))
        for frame, tile in enumerate(sequence):
            strip.paste(tile, (0, frame * 16))
        path = TEX / (name + '_glow.png')
        strip.save(path, optimize=True)
        path.with_suffix('.png.mcmeta').write_text(json.dumps({'animation': {
            'width': 16, 'height': 16, 'frametime': 2, 'interpolate': True,
        }}, indent=2) + '\n', encoding='utf-8')
    models = update_models()
    previews(images, frames, args.preview)
    print(f'SINGULARITY_REBUILT static={len(images)} animations={len(frames)} frames={FRAME_COUNT} modelsChanged={len(models)}')
    for name, mask in masks.items():
        print(f'{name}: emissivePixels={len(mask)} mode={ANIMATIONS[name]}')
    print('Model silhouettes, blockstates and base texture pixels are preserved.')


if __name__ == '__main__':
    main()
