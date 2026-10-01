"""Check the fixed-camera near-view sequence, including actual textured geometry pixels."""
from pathlib import Path
import argparse
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("captures", type=Path)
parser.add_argument("log", type=Path)
args = parser.parse_args()
log = args.log.read_text(encoding="utf-8", errors="replace")
assert "TAIXU_FLICKER_SEQUENCE_PASS frames=48 shaderReloads=2" in log, "Client sequence incomplete"
assert "TAIXU_SHADER_TOGGLE enabled=false" in log and "TAIXU_SHADER_TOGGLE enabled=true" in log
fractions = []
for frame in range(48):
    path = args.captures / f"taixu-near-{frame:03}.png"
    with Image.open(path) as image:
        assert image.size == (1600, 1000), "This fixture uses a fixed camera and resolution"
        image = image.convert("RGB")
        pixels = image.load()
        # The near tower fills this rectangle. Its 16x16 block texture has many
        # edges; an absent assembly leaves smooth sky/glow, even if all 11 entities exist.
        edges = sum(
            sum(abs(a - b) for a, b in zip(pixels[x, y], pixels[x - 1, y])) > 12
            for y in range(380, 580) for x in range(177, 320)
        )
        fraction = edges / (200 * 143)
        assert fraction >= .12, f"Missing textured assembly in {path.name}: edge fraction {fraction:.3f}"
        fractions.append(fraction)
print(f"TAIXU_VISIBLE_SEQUENCE_PASS frames=48 shaderReloads=2 minTextureEdges={min(fractions):.3f}")
