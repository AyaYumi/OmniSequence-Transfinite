"""Validate real-client captures and assemble the Tianyi opening preview."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("captures", type=Path)
parser.add_argument("log", type=Path)
parser.add_argument("--baseline", type=Path, required=True)
parser.add_argument("--patch", type=Path, required=True)
args = parser.parse_args()

log = args.log.read_text(encoding="utf-8", errors="replace")
assert "TIANYI_CLIENT_PASS openingFrames=33 trackedBodies=11" in log
assert "TIANYI_PAUSE_PASS" in log and "TIANYI_RESUME_PASS" in log
assert "TIANYI_SHADER_TOGGLE enabled=false" in log and "TIANYI_SHADER_TOGGLE enabled=true" in log
names = [f"tianyi-opening-{i:03d}.png" for i in range(33)]
names += ["tianyi-night-wide.png", "tianyi-night-near.png", "tianyi-crystal.png",
          "tianyi-day-wide.png", "tianyi-paused.png", "tianyi-resumed.png",
          "tianyi-no-shader.png", "tianyi-shader-reloaded.png"]
for name in names:
    with Image.open(args.captures / name) as image:
        assert image.size == (1600, 1000), name
        ranges = image.convert("RGB").getextrema()
        assert all(high - low > 100 for low, high in ranges), f"Blank capture: {name}"
        scene = image.convert("RGB").crop((400, 150, 1200, 900))
        highlights = sum(max(pixel) > 130 for pixel in scene.getdata())
        assert highlights > 100, f"Structure not drawn yet: {name}, highlights={highlights}"

with Image.open(args.captures / "tianyi-crystal.png") as image:
    pixels = list(image.convert("RGB").crop((550, 240, 1050, 740)).getdata())
cyan = sum(g > 95 and b > 100 and g > r * 1.12 and b > r * 1.12 for r, g, b in pixels)
assert cyan > 150, f"No visible cyan crystal facets: {cyan}"

with ZipFile(args.baseline) as baseline, ZipFile(args.patch) as patch:
    old, new = set(baseline.namelist()), set(patch.namelist())
    assert not old - new, "Patch removed release entries"
    changed = sorted(name for name in new if name not in old or baseline.read(name) != patch.read(name))
    prefix = "com/atir/molecularmanipulator/"
    allowed = {prefix + name + ".class" for name in [
        "entity/TaixuAssemblyEntity", "client/TaixuRenderer", "client/render/TaixuEffectState",
        "client/render/TaixuEffectLayers", "client/render/TaixuStructureEffects",
        "client/render/TaixuStructureEffects$Frame"]}
    assert set(changed) == allowed, f"Unexpected patched entries: {changed}"

frames = []
for i in range(33):
    with Image.open(args.captures / f"tianyi-opening-{i:03d}.png") as image:
        frames.append(image.convert("RGB").resize((960, 600), Image.Resampling.LANCZOS))
palette = frames[-1].quantize(colors=128)
animation = [frame.quantize(palette=palette, dither=Image.Dither.FLOYDSTEINBERG) for frame in frames]
animation[0].save(args.captures / "tianyi-opening.gif", save_all=True, append_images=animation[1:],
                  duration=[250] * 32 + [1500], loop=0, optimize=False)
report = {"captures": len(names), "openingFrames": 33, "cyanCrystalPixels": cyan,
          "baselineSha256": hashlib.sha256(args.baseline.read_bytes()).hexdigest(),
          "patchSha256": hashlib.sha256(args.patch.read_bytes()).hexdigest(),
          "changedEntries": changed}
(args.captures / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("TIANYI_VISIBLE_PASS " + json.dumps(report))
