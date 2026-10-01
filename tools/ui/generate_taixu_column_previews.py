from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
import math

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "taixu-column-previews"
OUT.mkdir(parents=True, exist_ok=True)
BASE = Image.open(ROOT / "build" / "taixu-embedded-delivery" / "embedded-night-wide.png").convert("RGB")
FONT = "C:/Windows/Fonts/NotoSansSC-VF.ttf"
FONT_BOLD = "C:/Windows/Fonts/NotoSansCJKsc-Bold.otf"

def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)

def glow_line(canvas, points, color, width=4, blur=14):
    glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).line(points, fill=(*color, 160), width=width * 4, joint="curve")
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(blur)))
    ImageDraw.Draw(canvas).line(points, fill=(*color, 255), width=width, joint="curve")

def pillar(canvas, x, y, height, width, palette, style):
    d = ImageDraw.Draw(canvas)
    top = y - height
    d.polygon([(x - width // 2, y), (x + width // 2, y), (x + width // 3, top), (x - width // 3, top)],
              fill=palette[0] + (255,), outline=palette[2] + (255,))
    d.polygon([(x + width // 2, y), (x + width // 3, top), (x + width // 2 + 12, top + 12), (x + width // 2 + 12, y)],
              fill=palette[1] + (255,))
    for offset in (0.25, 0.52, 0.78):
        yy = int(top + height * offset)
        d.rectangle((x - width // 2 - 4, yy - 5, x + width // 2 + 8, yy + 5), fill=palette[2] + (220,))
    d.rectangle((x - width // 2 - 12, top - 12, x + width // 2 + 18, top + 8), fill=palette[2] + (255,))
    d.rectangle((x - width // 2 - 10, y - 13, x + width // 2 + 15, y + 8), fill=palette[2] + (255,))
    if style == "armor":
        for side in (-1, 1):
            pts = [(x + side * (width // 2 + 4), top + 40), (x + side * (width // 2 + 42), top + 72),
                   (x + side * (width // 2 + 30), top + 126), (x + side * (width // 2 + 4), top + 92)]
            d.polygon(pts, fill=palette[3] + (180,), outline=palette[2] + (220,))
    elif style == "vein":
        glow_line(canvas, [(x, y - 12), (x - 9, top + height // 2), (x + 8, top + 80)], palette[3], 3, 10)
    elif style == "crown":
        d.polygon([(x - 34, top - 12), (x, top - 42), (x + 34, top - 12), (x + 20, top + 3), (x - 20, top + 3)],
                  fill=palette[3] + (255,), outline=palette[2] + (255,))

def central(canvas, cx, floor, style):
    d = ImageDraw.Draw(canvas)
    if style == "armor":
        palette = ((230, 236, 245), (111, 197, 220), (247, 203, 117), (183, 244, 255))
        pillar(canvas, cx, floor, 330, 76, palette, style)
        for yy, rx, ry in ((floor - 295, 116, 24), (floor - 220, 95, 20), (floor - 148, 80, 18)):
            d.ellipse((cx - rx, yy - ry, cx + rx, yy + ry), outline=palette[2] + (235,), width=5)
            glow_line(canvas, [(cx - rx, yy), (cx + rx, yy)], palette[3], 2, 8)
    elif style == "vein":
        palette = ((221, 236, 241), (73, 134, 153), (100, 234, 222), (188, 252, 255))
        pillar(canvas, cx, floor, 350, 60, palette, style)
        for angle in (-0.8, 0.8, -1.8, 1.8):
            ex = cx + int(math.sin(angle) * 135)
            ey = floor - 235 + int(math.cos(angle) * 60)
            glow_line(canvas, [(cx, floor - 100), (cx + int(math.sin(angle) * 45), floor - 180), (ex, ey)], palette[3], 3, 11)
        d.polygon([(cx, floor - 390), (cx + 34, floor - 330), (cx, floor - 265), (cx - 34, floor - 330)],
                  fill=palette[3] + (170,), outline=palette[2] + (255,))
    else:
        palette = ((235, 230, 220), (92, 81, 79), (233, 171, 91), (255, 235, 165))
        pillar(canvas, cx, floor, 305, 86, palette, style)
        for yy, rx, ry in ((floor - 285, 140, 26), (floor - 210, 112, 22), (floor - 136, 84, 18)):
            d.ellipse((cx - rx, yy - ry, cx + rx, yy + ry), outline=palette[2] + (235,), width=7)
        d.polygon([(cx - 55, floor - 376), (cx, floor - 448), (cx + 55, floor - 376), (cx + 24, floor - 312), (cx - 24, floor - 312)],
                  fill=palette[3] + (185,), outline=palette[2] + (255,))

def render_option(title, subtitle, style, palette, filename):
    W, H = 720, 760
    im = Image.new("RGBA", (W, H), (8, 16, 29, 255))
    d = ImageDraw.Draw(im)
    for i in range(80):
        x = (i * 97) % W; y = (i * 53) % 500
        d.ellipse((x, y, x + 2, y + 2), fill=(150, 200, 218, 105))
    d.ellipse((80, 350, 640, 520), fill=(24, 35, 48, 255), outline=(90, 141, 155, 255), width=3)
    d.ellipse((112, 376, 608, 493), outline=(225, 177, 101, 220), width=4)
    d.ellipse((142, 398, 578, 474), outline=(89, 207, 225, 220), width=3)
    cx, floor = 360, 535
    positions = [(170, 490, 155), (232, 438, 192), (305, 415, 225), (415, 415, 225),
                 (488, 438, 192), (550, 490, 155), (250, 510, 130), (470, 510, 130)]
    for x, y, height in positions:
        pillar(im, x, y, height, 44 if height < 180 else 50, palette, style)
    central(im, cx, floor, style)
    spoke = (182, 239, 250) if style == "armor" else (94, 238, 214) if style == "vein" else (255, 210, 119)
    start_y = floor - (185 if style == "armor" else 240 if style == "vein" else 280)
    for x, y, _ in positions:
        glow_line(im, [(cx, start_y), (x, y - 38)], spoke, 3 if style == "vein" else 2, 10)
    d.rounded_rectangle((20, 650, W - 20, 735), radius=16, fill=(17, 27, 43, 240), outline=palette[2] + (180,), width=2)
    d.text((40, 665), title, font=font(30, True), fill=(242, 247, 250, 255))
    d.text((40, 704), subtitle, font=font(19), fill=(177, 209, 218, 255))
    im.convert("RGB").save(OUT / filename, quality=95)

render_option("方案 A  ·  星环装甲柱", "分段外壳 + 斜向护翼；贴合现有建筑，施工改动中等", "armor",
              ((220, 228, 237), (80, 103, 122), (230, 183, 100), (150, 239, 252)), "option-a-star-armor.png")
render_option("方案 B  ·  晶脉神经柱", "贯穿式能量脉络 + 悬浮晶核；特效最强，运动识别度最高", "vein",
              ((207, 231, 232), (48, 88, 104), (80, 224, 203), (180, 255, 247)), "option-b-crystal-vein.png")
render_option("方案 C  ·  天穹冠冕柱", "厚重冠顶 + 三层环箍；仪式感最强，方块量最高", "crown",
              ((225, 217, 204), (77, 64, 62), (225, 163, 91), (255, 228, 151)), "option-c-sky-crown.png")

board = Image.new("RGB", (2160, 1320), (7, 13, 25))
bd = ImageDraw.Draw(board)
bd.text((70, 36), "太虚天枢 · 八柱与中央天柱优化概念预览", font=font(42, True), fill=(240, 246, 249))
bd.text((72, 94), "仅用于选择视觉方向；不会修改当前游戏。三种方案均保留三环、八塔运动和现有光效体系。", font=font(22), fill=(155, 192, 205))
for idx, filename in enumerate(("option-a-star-armor.png", "option-b-crystal-vein.png", "option-c-sky-crown.png")):
    panel = Image.open(OUT / filename).resize((650, 685), Image.Resampling.LANCZOS)
    board.paste(panel, (55 + idx * 700, 145))
ref = ImageEnhance.Brightness(BASE).enhance(0.62).resize((2010, 385), Image.Resampling.LANCZOS)
board.paste(ref, (75, 900))
bd = ImageDraw.Draw(board)
bd.rectangle((75, 900, 2085, 1285), outline=(106, 154, 169), width=2)
bd.rectangle((95, 925, 435, 972), fill=(8, 17, 29, 210))
bd.text((115, 936), "当前实拍参考 · 内嵌版", font=font(24, True), fill=(238, 246, 249))
board.save(OUT / "taixu-column-optimization-board.png", quality=95)
print(OUT / "taixu-column-optimization-board.png")
