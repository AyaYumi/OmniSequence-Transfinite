"""Original Taixu pixel art, native block models and data. No source images or resampling.

Every texture is authored on a 16x16 grid. Animated PNGs are vertical strips of
24 native 16x16 frames; only preview contact sheets are enlarged (nearest neighbor).
Run from any directory with Python + Pillow. Outputs are deterministic.
"""
from pathlib import Path
import json
import math
import random
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/molecularmanipulator"
DATA = ROOT / "src/main/resources/data"
TEX = ASSETS / "textures/block/taixu"
PREVIEW = ROOT / "build/taixu-palette"
NS = "molecularmanipulator:"
WHITE = (231, 235, 230)
HI = (250, 249, 234)
SILVER = (159, 179, 184)
SHADOW = (108, 135, 150)
GOLD = (201, 173, 113)
GOLD_HI = (243, 222, 163)
GOLD_DARK = (143, 117, 72)
INK = (33, 62, 81)
FACES = ("north", "south", "west", "east", "up", "down")
SURFACES = {}
MODELS = {}
BLOCKS = [
    ("taixu_creation_nexus", "太虚造化天枢", "Taixu Creation Nexus"),
    ("taixu_jade_casing", "太虚玉壳", "Taixu Jade Casing"),
    ("taixu_gilded_block", "造化金纹方块", "Creation Gilded Block"),
    ("taixu_pillar", "天枢立柱", "Celestial Axis Pillar"),
    ("taixu_ring_track", "周天环轨", "Celestial Ring Track"),
    ("taixu_glass", "太虚琉璃", "Taixu Glazed Glass"),
    ("taixu_conduit", "造化导流柱", "Creation Conduit"),
    ("taixu_collection_node", "摄灵阵眼", "Essence Collection Node"),
    ("taixu_genesis_core", "万象衍生核心", "Myriad Genesis Core"),
    ("taixu_stabilizer", "天枢稳定器", "Celestial Axis Stabilizer"),
    ("taixu_crystal_spire", "天穹晶塔", "Firmament Crystal Spire"),
    ("taixu_resource_port", "天枢资源接口", "Celestial Resource Port"),
    ("taixu_jade_stairs", "太虚玉壳楼梯", "Taixu Jade Stairs"),
    ("taixu_jade_slab", "太虚玉壳台阶", "Taixu Jade Slab"),
]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class Surface:
    def __init__(self, name, seed=0, transparent=False, animation="pulse"):
        self.name = name
        self.base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        if not transparent:
            rng = random.Random(seed)
            for y in range(16):
                for x in range(16):
                    grain = rng.choice([-2, -1, 0, 0, 0, 1, 2])
                    self.base.putpixel((x, y), tuple(v + grain for v in WHITE) + (255,))
        self.draw = ImageDraw.Draw(self.base)
        self.mask = Image.new("L", (16, 16), 0)
        self.light = ImageDraw.Draw(self.mask)
        self.animation = animation
        SURFACES[name] = self

    def frame(self):
        self.draw.rectangle((0, 0, 15, 15), outline=SHADOW)
        self.draw.line((1, 1, 14, 1), fill=HI)
        self.draw.line((1, 1, 1, 14), fill=HI)
        self.draw.line((2, 14, 14, 14), fill=SILVER)
        self.draw.line((14, 2, 14, 14), fill=SILVER)

    def gold(self, points):
        self.draw.line(points, fill=GOLD)

    def diamond(self, radius, color, center=(7.5, 7.5)):
        x, y = center
        points = [(int(x), int(y-radius)), (int(x+radius), int(y)),
                  (int(x+1), int(y+radius)), (int(x-radius), int(y+1)), (int(x), int(y-radius))]
        self.draw.line(points, fill=color)

    def energy(self, points, width=1):
        self.light.line(points, fill=255, width=width)

    def glow_frame(self, frame):
        out = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                if not self.mask.getpixel((x, y)):
                    continue
                if self.animation == "rise":
                    offset = y / 16
                elif self.animation == "flow":
                    offset = x / 16
                elif self.animation == "orbit":
                    offset = math.atan2(y-7.5, x-7.5) / (2*math.pi)
                elif self.animation == "gather":
                    offset = -math.hypot(x-7.5, y-7.5) / 8
                else:
                    offset = 0
                wave = (0.5 + 0.5 * math.cos(2*math.pi*(frame/24 + offset))) ** 3
                # Blue remains readable at the trough; highlights approach warm white.
                out.putpixel((x, y), (int(91+149*wave), int(174+72*wave), int(210+45*wave), 255))
        return out

    def save(self):
        self.base.save(TEX / (self.name + ".png"))
        if self.mask.getbbox():
            strip = Image.new("RGBA", (16, 16*24))
            for frame in range(24):
                strip.paste(self.glow_frame(frame), (0, frame*16))
            path = TEX / (self.name + "_glow.png")
            strip.save(path)
            write_json(path.with_suffix(".png.mcmeta"), {
                "animation": {"width": 16, "height": 16, "frametime": 2, "interpolate": True}})

    def preview(self, frame=0):
        return Image.alpha_composite(self.base, self.glow_frame(frame))


def paint():
    s = Surface("jade", 11)
    # Quiet architectural tile: the material can cover thousands of blocks.
    s.draw.line((0, 15, 15, 15), fill=SILVER)
    s.draw.line((15, 0, 15, 15), fill=SILVER)
    s.draw.line((0, 0, 14, 0), fill=HI)
    s.draw.line((0, 0, 0, 14), fill=HI)
    for x, y in [(3, 4), (9, 11), (12, 5)]:
        s.draw.line((x, y, x+1, y), fill=(215, 223, 219))

    s = Surface("gilded", 12)
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), outline=GOLD)
    for x, y in [(1, 1), (11, 1), (1, 11), (11, 11)]:
        s.draw.rectangle((x, y, x+3, y+3), outline=GOLD_DARK)
        s.draw.rectangle((x, y, x+2, y+2), outline=GOLD_HI)
    s.diamond(3, GOLD)
    s.draw.rectangle((7, 7, 8, 8), fill=GOLD_HI)

    s = Surface("pillar", 13)
    for x in (1, 5, 10, 14):
        s.draw.line((x, 0, x, 15), fill=SILVER)
        if x < 14:
            s.draw.line((x+1, 0, x+1, 15), fill=HI)
    s.draw.line((0, 0, 0, 15), fill=GOLD)
    s.draw.line((15, 0, 15, 15), fill=GOLD_HI)
    s = Surface("pillar_end", 14)
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), outline=SILVER)
    s.draw.rectangle((5, 5, 10, 10), outline=GOLD)
    s.draw.rectangle((7, 7, 8, 8), fill=GOLD_HI)

    s = Surface("ring_side", 15, animation="flow")
    for y, c in [(0, HI), (2, GOLD_HI), (3, GOLD_DARK), (6, SHADOW), (7, INK),
                 (8, INK), (9, SHADOW), (12, GOLD), (13, GOLD_DARK), (15, SILVER)]:
        s.draw.line((0, y, 15, y), fill=c)
    s.energy((0, 7, 15, 7))
    for x in (2, 10):
        s.draw.rectangle((x, 10, x+3, 11), outline=SILVER)
    s = Surface("ring_top", 16, animation="flow")
    for y, c in [(0, GOLD), (1, GOLD_HI), (3, SILVER), (12, SILVER), (14, GOLD_HI), (15, GOLD)]:
        s.draw.line((0, y, 15, y), fill=c)
    s.draw.line((0, 7, 15, 7), fill=INK)
    s.energy((0, 7, 15, 7))
    s.draw.line((3, 4, 6, 4, 9, 6), fill=GOLD)
    s.draw.line((3, 11, 6, 11, 9, 9), fill=GOLD)

    s = Surface("glass", transparent=True)
    s.draw.rectangle((0, 0, 15, 15), fill=(157, 212, 226, 24), outline=(190, 210, 204, 185))
    for x, y in [(0, 0), (13, 0), (0, 13), (13, 13)]:
        s.draw.rectangle((x, y, x+2, y+2), outline=(*GOLD, 225))
    # Keep glints clear of CTM's reflected three-texel border sample region.
    s.draw.line((6, 8, 8, 6), fill=(224, 246, 248, 65))

    s = Surface("conduit", 18, animation="rise")
    for x, c in [(0, SILVER), (1, HI), (3, GOLD), (4, SHADOW), (5, INK), (6, INK),
                 (9, INK), (10, INK), (11, SHADOW), (12, GOLD_HI), (14, HI), (15, SILVER)]:
        s.draw.line((x, 0, x, 15), fill=c)
    s.energy((6, 0, 6, 15)); s.energy((9, 0, 9, 15))
    for y in (3, 11):
        s.draw.line((7, y, 8, y), fill=GOLD)
    s = Surface("conduit_end", 19, animation="gather")
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), outline=GOLD)
    s.draw.rectangle((5, 5, 10, 10), fill=INK)
    s.energy((6, 6, 9, 6, 9, 9, 6, 9, 6, 6))

    s = Surface("socket", 20)
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), outline=GOLD)
    for y in (5, 7, 9):
        s.draw.line((5, y, 10, y), fill=SILVER)
    for p in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        s.draw.point(p, fill=GOLD_HI)

    s = Surface("controller", 21, animation="orbit")
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), fill=INK)
    s.diamond(6, GOLD_DARK); s.diamond(5, GOLD_HI)
    s.energy((7, 3, 7, 12)); s.energy((5, 6, 10, 6)); s.energy((5, 9, 10, 9))
    for p in [(2, 7), (13, 8), (7, 2), (8, 13)]:
        s.light.point(p, fill=255)

    s = Surface("collector", 22, animation="gather")
    s.frame()
    s.diamond(6, GOLD); s.diamond(4, SHADOW)
    s.draw.rectangle((6, 6, 9, 9), fill=INK)
    s.energy((7, 6, 8, 6, 9, 7, 9, 8, 8, 9, 7, 9, 6, 8, 6, 7, 7, 6))
    for points in [(7, 2, 7, 4), (11, 7, 13, 7), (8, 11, 8, 13), (2, 8, 4, 8)]:
        s.energy(points)

    s = Surface("stabilizer", 23, animation="pulse")
    s.frame()
    s.draw.rectangle((3, 3, 12, 12), outline=GOLD_DARK)
    s.draw.rectangle((4, 4, 11, 11), outline=GOLD_HI)
    s.draw.rectangle((6, 5, 9, 10), fill=INK)
    s.energy((7, 5, 7, 10)); s.energy((8, 5, 8, 10))
    for points in [(2, 7, 5, 7), (10, 8, 13, 8), (7, 2, 7, 4), (8, 11, 8, 13)]:
        s.gold(points)

    s = Surface("port", 24, animation="flow")
    s.frame()
    s.draw.rectangle((3, 4, 12, 11), fill=GOLD_DARK)
    s.draw.rectangle((4, 5, 11, 10), fill=INK)
    for x in range(5, 11, 2):
        s.draw.line((x, 6, x, 9), fill=SILVER)
    s.energy((5, 2, 10, 2)); s.energy((5, 13, 10, 13))
    s.gold((2, 6, 2, 9)); s.gold((13, 6, 13, 9))

    s = Surface("crystal", 25, animation="rise")
    for y in range(16):
        for x in range(16):
            band = abs(x-7.5)/7.5
            s.base.putpixel((x, y), (int(74+110*(1-band)), int(147+75*(1-band)), int(184+53*(1-band)), 255))
    s.draw.line((3, 0, 3, 15), fill=(213, 244, 250))
    s.draw.line((11, 0, 11, 15), fill=(84, 156, 185))
    s.energy((7, 0, 7, 15)); s.energy((8, 0, 8, 15))
    s = Surface("core_seal", 26, animation="orbit")
    s.frame()
    s.diamond(6, GOLD); s.diamond(4, GOLD_HI)
    s.draw.rectangle((6, 6, 9, 9), fill=INK)
    s.energy((7, 6, 8, 6, 9, 7, 9, 8, 8, 9, 7, 9, 6, 8, 6, 7, 7, 6))


def texture(name):
    return NS + "block/taixu/" + name


EMISSION = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def element(start, end, surfaces, glow=False, cull=False):
    if isinstance(surfaces, str):
        surfaces = dict.fromkeys(FACES, surfaces)
    faces = {}
    for face, surface in surfaces.items():
        if glow and not SURFACES[surface].mask.getbbox():
            continue
        faces[face] = {"texture": "#" + surface + ("_glow" if glow else "")}
        if cull:
            faces[face]["cullface"] = face
        if glow:
            faces[face]["uv"] = [0, 0, 16, 16]
    entry = {"from": start, "to": end, "faces": faces}
    if glow:
        entry["shade"] = False
        entry["neoforge_data"] = EMISSION
    return entry


def model(name, elements, surfaces, render_type="cutout"):
    textures = {key: texture(key) for key in surfaces}
    for key in surfaces:
        if SURFACES[key].mask.getbbox():
            textures[key + "_glow"] = texture(key + "_glow")
    textures["particle"] = texture(surfaces[0])
    value = {"parent": "minecraft:block/block", "render_type": render_type,
             "textures": textures, "elements": elements}
    MODELS[name] = value
    write_json(ASSETS / "models/block" / (name + ".json"), value)


def cube(name, side, top=None, front=None, translucent=False):
    mapping = dict.fromkeys(FACES, side)
    if top:
        mapping.update(up=top, down=top)
    if front:
        mapping["north"] = front
    elements = [element([0, 0, 0], [16, 16, 16], mapping, cull=True)]
    if any(SURFACES[key].mask.getbbox() for key in mapping.values()):
        elements.append(element([-0.005]*3, [16.005]*3, mapping, glow=True, cull=True))
    model(name, elements, list(dict.fromkeys(mapping.values())), "translucent" if translucent else "cutout")


def native_models():
    cube("taixu_creation_nexus", "socket", "core_seal", "controller")
    cube("taixu_jade_casing", "jade")
    cube("taixu_gilded_block", "gilded")
    cube("taixu_pillar", "pillar", "pillar_end")
    cube("taixu_ring_track", "ring_side", "ring_top")
    cube("taixu_glass", "glass", translucent=True)
    cube("taixu_conduit", "conduit", "conduit_end")
    cube("taixu_collection_node", "socket", "pillar_end", "collector")
    cube("taixu_stabilizer", "socket", "pillar_end", "stabilizer")
    cube("taixu_resource_port", "socket", "pillar_end", "port")

    # Open cage, with real negative space and a small static crystal even when effects are disabled.
    elements = [element([0, 0, 0], [16, 2, 16], "gilded"),
                element([0, 14, 0], [16, 16, 16], "gilded")]
    for x, z in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        elements.append(element([x, 2, z], [x+2, 14, z+2], "pillar"))
    elements.append(element([7, 5, 7], [9, 11, 9], "crystal"))
    elements[-1]["neoforge_data"] = EMISSION
    elements.append(element([6, 2, 6], [10, 3, 10], "gilded"))
    elements.append(element([6, 13, 6], [10, 14, 10], "gilded"))
    model("taixu_genesis_core", elements, ["gilded", "pillar", "crystal"])

    elements = []
    sections = [(2, 0, 14, 3, "gilded"), (4, 3, 12, 5, "pillar_end"),
                (5, 5, 11, 10, "crystal"), (6, 10, 10, 14, "crystal"), (7, 14, 9, 16, "crystal")]
    for lo, a, hi, b, surface in sections:
        part = element([lo, a, lo], [hi, b, hi], surface)
        if surface == "crystal":
            part["neoforge_data"] = {"block_light": 12, "sky_light": 12, "ambient_occlusion": False}
        elements.append(part)
        if surface == "crystal":
            elements.append(element([lo-.005, a-.005, lo-.005], [hi+.005, b+.005, hi+.005], surface, glow=True))
    model("taixu_crystal_spire", elements, ["gilded", "pillar_end", "crystal"])

    for suffix, parent in [("stairs", "stairs"), ("stairs_inner", "inner_stairs"),
                           ("stairs_outer", "outer_stairs"), ("slab", "slab"), ("slab_top", "slab_top")]:
        value = {"parent": "minecraft:block/" + parent,
                 "textures": dict.fromkeys(("bottom", "top", "side"), texture("jade"))}
        MODELS["taixu_jade_" + suffix] = value
        write_json(ASSETS / "models/block" / ("taixu_jade_" + suffix + ".json"), value)


def variant(name, x=0, y=0, uvlock=False):
    result = {"model": NS + "block/" + name}
    if x: result["x"] = x
    if y: result["y"] = y
    if uvlock: result["uvlock"] = True
    return result


def blockstates_and_data():
    directional = {"taixu_creation_nexus", "taixu_ring_track", "taixu_collection_node", "taixu_stabilizer", "taixu_resource_port"}
    for name, _, _ in BLOCKS:
        if name in directional:
            variants = {"facing="+f: variant(name, y=r) for f, r in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}
        elif name in ("taixu_pillar", "taixu_conduit"):
            variants = {"axis=y": variant(name), "axis=x": variant(name, 90, 90), "axis=z": variant(name, 90)}
        elif name == "taixu_crystal_spire":
            variants = {"facing=up": variant(name), "facing=down": variant(name, 180),
                        "facing=north": variant(name, 90), "facing=south": variant(name, 90, 180),
                        "facing=east": variant(name, 90, 90), "facing=west": variant(name, 90, 270)}
        elif name == "taixu_jade_slab":
            variants = {"type=bottom": variant(name), "type=top": variant(name+"_top"), "type=double": variant("taixu_jade_casing")}
        elif name == "taixu_jade_stairs":
            variants = {}
            # Same orientation contract as vanilla stairs (canonical facing=east).
            for facing, rotation in [("east", 0), ("south", 90), ("west", 180), ("north", 270)]:
                for half in ("bottom", "top"):
                    for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                        y = rotation
                        if shape.endswith("left"): y -= 90
                        if half == "top" and shape != "straight": y += 90
                        suffix = "_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else ""
                        variants[f"facing={facing},half={half},shape={shape}"] = variant(name+suffix, 180 if half=="top" else 0, y%360, half=="top" or y%360!=0)
        else:
            variants = {"": variant(name)}
        write_json(ASSETS / "blockstates" / (name+".json"), {"variants": variants})
        write_json(ASSETS / "models/item" / (name+".json"), {"parent": NS+"block/"+name})
        entry = {"type": "minecraft:item", "name": NS+name}
        if name == "taixu_jade_slab":
            entry["functions"] = [{"function": "minecraft:set_count", "count": 2,
                "conditions": [{"condition": "minecraft:block_state_property", "block": NS+name, "properties": {"type": "double"}}]}]
        write_json(DATA / "molecularmanipulator/loot_table/blocks" / (name+".json"),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    # Shared files are extended, never regenerated from an older snapshot.
    for locale, index in [("zh_cn", 1), ("en_us", 2)]:
        path = ASSETS / "lang" / (locale+".json")
        values = json.loads(path.read_text(encoding="utf-8"))
        for block in BLOCKS:
            values["block.molecularmanipulator."+block[0]] = block[index]
        values["tooltip.molecularmanipulator.taixu.lore"] = (
            "悬于太虚，摄诸天之精；运转造化，衍万物之形。" if index == 1 else
            "Suspended in the great void, drawing the essence of the heavens; turning creation, giving form to all things.")
        values["tooltip.molecularmanipulator.taixu.palette"] = (
            "太虚造化天枢 · 建筑部件" if index == 1 else "Taixu Creation Nexus · Building component")
        write_json(path, values)

    for tag, names in [("mineable/pickaxe", [b[0] for b in BLOCKS]),
                       ("stairs", ["taixu_jade_stairs"]), ("slabs", ["taixu_jade_slab"])]:
        path = DATA / "minecraft/tags/block" / (tag+".json")
        obj = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        for name in names:
            if NS+name not in obj["values"]: obj["values"].append(NS+name)
        write_json(path, obj)
    write_json(DATA / "molecularmanipulator/tags/block/taixu_structure_parts.json",
               {"replace": False, "values": [NS+b[0] for b in BLOCKS]})


def previews():
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()
    title_font = ImageFont.truetype(str(font_path), 30) if font_path.exists() else font
    sheet = Image.new("RGB", (1120, 840), (20, 30, 44))
    d = ImageDraw.Draw(sheet)
    d.text((32, 20), "太虚造化天枢  /  原生 16 × 16 材质", font=title_font, fill=(238, 225, 188))
    d.text((32, 66), "珍珠白玉 · 淡金刻纹 · 冰蓝光流     每格为独立原生像素，放大仅用于展示", font=font, fill=(173, 195, 210))
    labels = ["太虚玉壳", "造化金纹", "天枢立柱", "立柱端面", "环轨侧面", "环轨顶面", "太虚琉璃", "导流柱侧面", "导流柱端面",
              "功能部件侧面", "天枢控制面", "摄灵阵眼", "稳定器", "资源接口", "天穹晶体", "核心封印"]
    for i, (surface, label) in enumerate(zip(SURFACES.values(), labels)):
        x, y = 32+(i%6)*182, 120+(i//6)*230
        d.rounded_rectangle((x-6, y-6, x+165, y+201), radius=10, fill=(30, 43, 58))
        tile = surface.preview().resize((160, 160), Image.Resampling.NEAREST)
        sheet.paste(tile, (x, y), tile)
        d.text((x, y+173), label, font=font, fill=(216, 228, 232))
    sheet.save(PREVIEW / "taixu-textures.png")

    animated = [s for s in SURFACES.values() if s.mask.getbbox()]
    frames = []
    for tick in range(24):
        image = Image.new("RGB", (800, 390), (20, 30, 44))
        draw = ImageDraw.Draw(image)
        draw.text((20, 10), "太虚造化天枢 · 原生纹理动画预览", font=title_font, fill=(238, 225, 188))
        for i, surface in enumerate(animated):
            x, y = 20+(i%5)*156, 62+(i//5)*165
            tile = surface.preview(tick).resize((128, 128), Image.Resampling.NEAREST)
            image.paste(tile, (x, y), tile)
        frames.append(image)
    frames[0].save(PREVIEW / "taixu-textures.gif", save_all=True, append_images=frames[1:], duration=100, loop=0, disposal=2)


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    paint()
    for surface in SURFACES.values(): surface.save()
    native_models()
    blockstates_and_data()
    previews()
    print(f"TAIXU_GENERATED blocks={len(BLOCKS)} surfaces={len(SURFACES)} animations={sum(bool(s.mask.getbbox()) for s in SURFACES.values())} frame=16x16")


if __name__ == "__main__":
    main()
