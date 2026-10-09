"""Animate the approved 16px crystal artwork and generate a soft emissive fog sprite."""
from pathlib import Path
from PIL import Image
import json
import math

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / 'src/main/resources/assets/molecularmanipulator/textures'
base = Image.open(ROOT / 'tools/assets/ghost_matter_base.png').convert('RGBA')
assert base.size == (16, 16)
frames = []
for frame in range(32):
    t = frame / 32
    image = Image.new('RGBA', base.size)
    for y in range(16):
        for x in range(16):
            r, g, b, a = base.getpixel((x, y))
            if not a:
                continue
            pulse = .87 + .13 * math.sin(math.tau * (t + x * .025 - y * .018))
            shimmer = max(0, math.cos(math.tau * (t - y / 16 + x / 32))) ** 12
            color = [r * pulse + 36 * shimmer, g * pulse + 18 * shimmer, b * pulse + 28 * shimmer]
            image.putpixel((x, y), (*[int(min(255, v)) for v in color], a))
    frames.append(image)
strip = Image.new('RGBA', (16, 16 * len(frames)))
for index, frame in enumerate(frames):
    strip.paste(frame, (0, index * 16))
target = TEXTURES / 'item/ghost_matter.png'
strip.save(target)
target.with_suffix('.png.mcmeta').write_text(json.dumps({
    'animation': {'frametime': 2, 'interpolate': True, 'width': 16, 'height': 16}
}, indent=2) + '\n', encoding='utf-8')

# The renderer supplies the cyan-green tint; feathered alpha avoids visible square billboard edges.
fog = Image.new('RGBA', (128, 128))
for y in range(128):
    for x in range(128):
        u, v = (x + .5 - 64) / 64, (y + .5 - 64) / 64
        radial = max(0, 1 - u * u - v * v)
        warp = .1 * math.sin(4 * v + 2 * u)
        wisp = .65 + .18 * math.sin(5 * (v + warp) + 2 * u) + .12 * math.cos(7 * u + 3 * v)
        alpha = int(255 * radial ** 2 * max(.12, min(1, wisp)))
        fog.putpixel((x, y), (210, 255, 246, alpha))
(TEXTURES / 'effect').mkdir(exist_ok=True)
fog.save(TEXTURES / 'effect/ghost_matter_mist.png')

# Real faceted meshes replace the original crossed sprite. The same deposit contains upright and fallen shards.
MODELS = ROOT / 'src/main/resources/assets/molecularmanipulator/models/block'
facets = {'dark': (36, 61, 73), 'blue': (65, 105, 126),
          'pale': (132, 176, 194), 'glint': (212, 232, 234)}
(TEXTURES / 'block/ghost_matter').mkdir(parents=True, exist_ok=True)
for name, rgb in facets.items():
    texture = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            variation = .94 + .06 * (1 - y / 15) + .012 * math.sin(x * 1.8 + y * .9)
            texture.putpixel((x, y), (*[int(min(255, c * variation)) for c in rgb], 255))
    texture.save(TEXTURES / f'block/ghost_matter/{name}.png')
mtl = []
for name in facets:
    mtl.extend([f'newmtl {name}', 'Ka 0 0 0', 'Kd 1 1 1', 'd 1', f'map_Kd #{name}', ''])
(MODELS / 'ghost_matter.mtl').write_text('\n'.join(mtl), encoding='utf-8')
obj = ['# Faceted Ghost Matter deposit; model units are blocks', 'mtllib ghost_matter.mtl']
vertex_count = 0
crystals = [(.22, .23, .27, .060, 8, 30), (.70, .24, .34, .073, -12, 110),
            (.49, .49, .21, .068, 17, 230), (.20, .75, .14, .058, 75, 80),
            (.77, .76, .18, .061, 67, 230), (.45, .84, .24, .057, -6, 110),
            (.84, .50, .12, .048, 32, 335)]
for shard, (cx, cz, height, radius, lean_deg, heading_deg) in enumerate(crystals):
    obj.append(f'o Crystal_{shard}')
    local = []
    for y, factor in [(0, .72), (height * .68, 1), (height * .83, .8)]:
        for side in range(6):
            angle = math.tau * side / 6
            local.append((radius * factor * math.cos(angle), y, radius * factor * math.sin(angle)))
    local.extend([(radius * .23, height, radius * .13), (0, 0, 0)])
    lean, heading = math.radians(lean_deg), math.radians(heading_deg)
    points = []
    for x, y, z in local:
        a, b = x * math.cos(lean) + y * math.sin(lean), -x * math.sin(lean) + y * math.cos(lean)
        points.append((cx + a * math.cos(heading) - z * math.sin(heading), b,
                       cz + a * math.sin(heading) + z * math.cos(heading)))
    floor = min(v[1] for v in points)
    points = [(x, y - floor + .005, z) for x, y, z in points]
    center = tuple(sum(v[axis] for v in points) / len(points) for axis in range(3))
    faces = []
    for side in range(6):
        nxt = (side + 1) % 6
        body_material = ['blue', 'dark', 'pale', 'blue', 'dark', 'blue'][side]
        faces.extend([([side, nxt, nxt + 6, side + 6], body_material),
                      ([side + 6, nxt + 6, nxt + 12, side + 12], 'glint' if side == 2 else body_material),
                      ([side + 12, nxt + 12, 18], 'glint' if side == 1 else 'pale'),
                      ([19, nxt, side], 'dark')])
    for indices, material in faces:
        face = [points[i] for i in indices]
        ab = [face[1][a] - face[0][a] for a in range(3)]
        ac = [face[2][a] - face[0][a] for a in range(3)]
        normal = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0]]
        mid = [sum(v[a] for v in face)/len(face) for a in range(3)]
        if sum(normal[a] * (mid[a]-center[a]) for a in range(3)) < 0:
            face.reverse()
        obj.append(f'usemtl {material}')
        start = vertex_count + 1
        for vertex, uv in zip(face, [(0,1), (1,1), (1,0), (0,0)]):
            assert all(0 <= coordinate <= 1 for coordinate in vertex), vertex
            obj.extend(['v %.8f %.8f %.8f' % vertex, 'vt %d %d' % uv])
            vertex_count += 1
        obj.append('f ' + ' '.join(f'{i}/{i}' for i in range(start, vertex_count + 1)))
(MODELS / 'ghost_matter.obj').write_text('\n'.join(obj) + '\n', encoding='utf-8')
model = {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'cutout',
         'textures': {name: f'molecularmanipulator:block/ghost_matter/{name}' for name in facets},
         'loader': 'neoforge:obj', 'model': 'molecularmanipulator:models/block/ghost_matter.obj',
         'automatic_culling': False, 'flip_v': True, 'shade_quads': True, 'emissive_ambient': False}
model['textures']['particle'] = model['textures']['blue']
(MODELS / 'ghost_matter.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')

preview = ROOT / 'build/reports/ghost-matter-animation'
preview.mkdir(parents=True, exist_ok=True)
large = [im.resize((256, 256), Image.Resampling.NEAREST) for im in frames]
large[0].save(preview / 'ghost-matter-icon.gif', save_all=True, append_images=large[1:],
              duration=100, loop=0, disposal=2, transparency=0)
print(f'Ghost Matter: {len(frames)} unique frames, {strip.size}; mist {fog.size}')
print(f'Placed deposit: {len(crystals)} faceted crystals, {vertex_count} vertices')
