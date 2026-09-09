"""Bake the first texture-animation study without moving or redrawing source texels.

Requires Pillow. Run from any directory; originals in static/ are the editable source.
PNG strips and metadata go into the mod; GIF previews go into build/texture-animation-v1.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "static"
TARGET = ROOT / "src/main/resources/assets/molecularmanipulator/textures/block"
PREVIEW = ROOT / "build/texture-animation-v1"
FRAMES = 24
TICKS = 2


def gold(p):
    r, g, b, a = p
    return a >= 128 and r >= 100 and g >= 85 and r - b >= 20 and g - b >= 7 and r >= g - 4 and r - g <= 75


def blue(p):
    r, g, b, a = p
    return a >= 128 and b >= 70 and g >= 50 and b - r >= 25 and b - g >= 12 and g - r >= 12


def material(name, p, x, y):
    if name.startswith("matter_fabrication_"):
        if gold(p):
            return "gold"
        if name.startswith("matter_fabrication_fluid_") and blue(p):
            return "blue"
        return None
    r, g, b, a = p
    if "coil" in name or "pylon" in name:
        if not (2 <= y <= 13 and (2 <= x <= 6 or 9 <= x <= 13)):
            return None
    if a >= 128 and b >= 70 and (b - g >= 18 or r - g >= 25):
        return "violet" if r >= g else "crystal_blue"
    return None


def trail(position, time):
    # One smooth, narrow leading highlight with a longer trailing tail.
    behind = (time - position) % 1
    return max(math.exp(-behind * 11), math.exp(-((1 - behind) / 0.045) ** 2))


def breathe(time, phase=0.0):
    return 0.22 + 0.78 * (0.5 + 0.5 * math.sin(math.tau * (time + phase)))


def group_pulse(group, time, groups=4):
    # A short group response, with all groups visible between pulses.
    phase = (time * groups - group) % groups
    distance = min(phase, groups - phase)
    return 0.18 + 0.82 * math.exp(-((distance / 0.32) ** 2))


def light(name, x, y, time):
    dx, dy = x - 7.5, y - 7.5
    radius = max(abs(dx), abs(dy)) / 8
    angle = (math.atan2(dy, dx) / math.tau) % 1
    if name == "matter_fabrication_coil":
        return trail((x + 0.5) / 16, time)
    if name == "matter_fabrication_stabilizer":
        return breathe(time, 0.08)
    if name.startswith("matter_fabrication_"):
        if name.endswith("_input"):
            return breathe(time, 0.0 if x + y < 16 else 0.5)
        if name.endswith("_output"):
            return breathe(time, 0.5 if x + y < 16 else 0.0)
        if "controller" in name or "pattern_assembly" in name:
            return group_pulse((x // 4 + y // 4) % 4, time)
        return breathe(time)
    if name == "computation_data_entangler":
        upper = x + y < 15
        return breathe(time, 0.0 if upper else 0.5)
    if name == "infinite_parallel_matrix":
        return group_pulse((x // 4 + y // 4) % 4, time)
    if name == "universal_pattern_matrix":
        # Interference: two static diagonals strengthen and cancel at different times.
        return 0.22 + 0.78 * (0.5 + 0.5 * math.sin(math.tau * time + (x - y) * 0.32))
    if "coil" in name or "pylon" in name:
        # Crystal faces catch light independently instead of carrying a travelling dot.
        phase = ((x * 3 + y * 5) % 7) / 7
        return 0.3 + 0.7 * (0.5 + 0.5 * math.sin(math.tau * (time * 0.72 + phase)))
    if radius < 0.3:
        return breathe(time)
    return breathe(time, angle * 0.12)


def mix(a, b, fraction):
    return tuple(round(x + (y - x) * fraction) for x, y in zip(a, b))


def tint(pixel, kind, intensity):
    palettes = {
        "gold": ((160, 132, 64), (255, 240, 178)),
        "blue": ((38, 65, 98), (129, 204, 255)),
        "violet": ((85, 30, 128), (242, 191, 255)),
        "crystal_blue": ((35, 66, 124), (147, 215, 255)),
    }
    dark, bright = palettes[kind]
    body = mix(pixel[:3], dark, 0.48 * (1 - intensity))
    color = mix(body, bright, intensity * 0.78)
    return (*color, pixel[3])


def build(name):
    original = Image.open(SOURCE / (name + ".png")).convert("RGBA")
    assert original.size == (16, 16)
    frames = []
    for frame in range(FRAMES):
        image = original.copy()
        for y in range(16):
            for x in range(16):
                pixel = original.getpixel((x, y))
                kind = material(name, pixel, x, y)
                if kind:
                    result = tint(pixel, kind, light(name, x, y, frame / FRAMES))
                    if kind == "gold":
                        assert gold(result), (name, frame, x, y, result)
                    if kind == "blue":
                        assert blue(result), (name, frame, x, y, result)
                    image.putpixel((x, y), result)
        frames.append(image)
    assert len({f.tobytes() for f in frames}) > 1, name
    strip = Image.new("RGBA", (16, 16 * FRAMES))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, index * 16))
    strip.save(TARGET / (name + ".png"))
    metadata = {"animation": {"frametime": TICKS, "interpolate": True, "width": 16, "height": 16}}
    (TARGET / (name + ".png.mcmeta")).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return frames


def font(size):
    for path in (Path("C:/Windows/Fonts/msyh.ttc"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")):
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def contact_gif(name, title, entries, animations, columns=4):
    cell_w, cell_h = 230, 217
    width = columns * cell_w
    height = 65 + math.ceil(len(entries) / columns) * cell_h
    output = []
    for tick in range(FRAMES * TICKS):
        canvas = Image.new("RGB", (width, height), (25, 28, 35))
        draw = ImageDraw.Draw(canvas)
        draw.text((20, 15), title, font=font(20), fill=(234, 237, 246))
        draw.text((20, 42), "实际贴图帧 · 2.4 秒循环 · 外壳与轮廓保持不变", font=font(12), fill=(156, 166, 188))
        frame_index, subframe = divmod(tick, TICKS)
        for index, (texture, label) in enumerate(entries):
            x, y = (index % columns) * cell_w, 65 + (index // columns) * cell_h
            frames = animations[texture]
            image = Image.blend(frames[frame_index], frames[(frame_index + 1) % FRAMES], subframe / TICKS)
            image = image.resize((160, 160), Image.Resampling.NEAREST)
            canvas.paste(image, (x + 35, y + 5), image)
            draw.text((x + cell_w / 2, y + 182), label, font=font(14), fill=(236, 236, 241), anchor="mm")
        output.append(canvas)
    output[0].save(PREVIEW / (name + ".gif"), save_all=True, append_images=output[1:], duration=50, loop=0, optimize=False)
    output[0].save(PREVIEW / (name + ".png"))


def main():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    animations = {p.stem: build(p.stem) for p in sorted(SOURCE.glob("*.png"))}
    contact_gif("violet-preview", "蓝紫演算系列 / 第一版", [
        ("computation_data_entangler", "数据纠缠节点"),
        ("infinite_parallel_matrix", "无限并行矩阵"),
        ("universal_pattern_matrix", "全知演算矩阵"),
        ("omni_computation_controller_on", "万物演算核心 · 正面"),
        ("molecular_center_coil", "构序阵列量子水晶"),
        ("computation_crystal_pylon", "演算晶体尖塔"),
        ("omni_computation_controller_frame_on", "万物演算核心 · 框架"),
    ], animations)
    contact_gif("matter-flow-preview", "物质构筑井 / 金色与蓝色流光", [
        ("matter_fabrication_coil", "线圈 · 横向流动"),
        ("matter_fabrication_stabilizer", "稳定器 · 轻呼吸"),
        ("matter_fabrication_controller_on", "控制器 · 分段响应"),
        ("matter_fabrication_pattern_assembly", "样板总成 · 分段读取"),
        ("matter_fabrication_item_input", "物品输入 · 间歇脉冲"),
        ("matter_fabrication_item_output", "物品输出 · 间歇脉冲"),
        ("matter_fabrication_fluid_input", "流体输入 · 间歇脉冲"),
        ("matter_fabrication_fluid_output", "流体输出 · 间歇脉冲"),
    ], animations)
    print(json.dumps({"textures": len(animations), "frames_per_texture": FRAMES, "cycle_seconds": FRAMES * TICKS / 20,
                      "preview": str(PREVIEW)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
