"""Check resource links/frame sizes and package the native-model diagnostic previews."""
import json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw, ImageFont
from generate_taixu import ROOT, ASSETS, TEX, PREVIEW, BLOCKS


def verify():
    animations = 0
    for path in TEX.glob("*.png"):
        with Image.open(path) as image:
            assert image.width == 16 and image.height % 16 == 0, path
            meta = path.with_suffix(".png.mcmeta")
            if meta.exists():
                obj = json.loads(meta.read_text())["animation"]
                assert (obj["width"], obj["height"]) == (16, 16)
                assert image.height == 24*16 and obj["frametime"] == 2
                assert obj["interpolate"] is True
                alpha = image.crop((0, 0, 16, 16)).getchannel("A").tobytes()
                frames = []
                for frame in range(24):
                    tile = image.crop((0, frame*16, 16, (frame+1)*16))
                    assert tile.getchannel("A").tobytes() == alpha, "Animation changes its material mask"
                    frames.append(tile.tobytes())
                assert len(set(frames)) > 8, path
                animations += 1
            else:
                assert image.size == (16, 16), path
    with ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.220-client-extra-aka-minecraft-resources.jar") as vanilla:
        for path in (ASSETS / "models/block").glob("taixu_*.json"):
            obj = json.loads(path.read_text())
            if "parent" in obj:
                namespace, location = obj["parent"].split(":")
                assert "assets/minecraft/models/"+location+".json" in vanilla.namelist() if namespace=="minecraft" else (ASSETS / "models" / (location+".json")).exists()
            for location in obj.get("textures", {}).values():
                assert (ASSETS / "textures" / (location.split(":")[1]+".png")).exists(), location
        reference = json.loads(vanilla.read("assets/minecraft/blockstates/quartz_stairs.json"))["variants"]
        actual = json.loads((ASSETS / "blockstates/taixu_jade_stairs.json").read_text())["variants"]
        assert actual.keys() == reference.keys()
        for key, variant in actual.items():
            for axis in ("x", "y"):
                assert variant.get(axis, 0) == reference[key].get(axis, 0), (key, axis)
            assert variant.get("uvlock", False) == reference[key].get("uvlock", False), key
    for name, zh, en in BLOCKS:
        for directory in ("blockstates", "models/item"):
            assert (ASSETS / directory / (name+".json")).exists()
        assert (ROOT / "src/main/resources/data/molecularmanipulator/loot_table/blocks" / (name+".json")).exists()
        for locale, expected in (("zh_cn",zh),("en_us",en)):
            lang = json.loads((ASSETS / "lang" / (locale+".json")).read_text(encoding="utf-8"))
            assert lang["block.molecularmanipulator."+name] == expected
        state = json.loads((ASSETS / "blockstates" / (name+".json")).read_text())
        for variant in state["variants"].values():
            assert (ASSETS / "models" / (variant["model"].split(":")[1]+".json")).exists()
    print(f"TAIXU_RESOURCE_PASS blocks={len(BLOCKS)} animations={animations} frame=16x16 vanillaStairs=40")


def previews():
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 18)
    heading = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 30)
    bg = (12, 18, 28)
    with Image.open(PREVIEW / "taixu-models-raw.png") as raw:
        page = Image.new("RGB", (1400, 1120), bg)
        d = ImageDraw.Draw(page)
        d.text((24, 16), "太虚造化天枢 · 第一版方块", font=heading, fill=(239, 220, 176))
        d.text((24, 60), "原生模型烘焙预览  /  日间材质与暗处自发光  /  非游戏内截图", font=font, fill=(173, 195, 210))
        for row in range(4):
            y = 100+row*250
            page.paste(raw.crop((0,row*200,1400,(row+1)*200)),(0,y))
            for col in range(7):
                label = BLOCKS[(row%2)*7+col][1]
                d.text((col*200+10,y+205),label,font=font,fill=(216,228,232))
        page.save(PREVIEW / "taixu-blocks.png")
    with Image.open(PREVIEW / "taixu-connected-raw.png") as raw:
        page = Image.new("RGB", (1360, 680), bg)
        d = ImageDraw.Draw(page)
        d.text((20, 16), "太虚造化天枢 · 连接纹理", font=heading, fill=(239,220,176))
        d.text((20, 64), "原生模型、真实相邻方块连接掩码；保留外轮廓与中心纹饰",font=font,fill=(173,195,210))
        labels = ["太虚玉壳","太虚琉璃","周天环轨","造化导流柱","天枢控制面","造化金纹"]
        d.text((20,105),"连接前",font=font,fill=(173,195,210))
        page.paste(raw.crop((0,90,1320,290)),(20,140))
        d.text((20,370),"连接后",font=font,fill=(173,195,210))
        page.paste(raw.crop((0,450,1320,650)),(20,405))
        for i,label in enumerate(labels): d.text((35+i*220,622),label,font=font,fill=(216,228,232))
        page.save(PREVIEW / "taixu-connected.png")
    # A 12-second excerpt with the same speed and geometry as the in-game renderer.
    frames = [Image.open(PREVIEW / f"core-{i:02d}.png").convert("RGB") for i in range(48)]
    frames[0].save(PREVIEW / "taixu-core.gif", save_all=True, append_images=frames[1:], duration=250, loop=0, disposal=2)
    for image in frames: image.close()
    print("TAIXU_PREVIEWS_READY build/taixu-palette")


if __name__ == "__main__":
    verify()
    if (PREVIEW / "taixu-connected-raw.png").exists(): previews()
