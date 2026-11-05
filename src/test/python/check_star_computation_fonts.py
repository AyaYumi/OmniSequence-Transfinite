"""Check entropy UI font coverage, or rebuild its single-glyph, same-weight fallbacks.

Check:   python src/test/python/check_star_computation_fonts.py
Rebuild: python src/test/python/check_star_computation_fonts.py --source /path/to/NotoSansSC-VF.ttf
Requires fontTools; never modifies the existing osq_sc_*.ttf or global font definitions.
The source is the OFL Noto Sans SC / Source Han Sans design (see licenses/OFL-NSC.txt).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets/molecularmanipulator"
FONTS = ASSETS / "font"
GLYPHS = "熵"
WEIGHTS = {500: "Medium", 600: "Semibold", 700: "Bold", 900: "Black"}
FACES = ("500_5", "600_5_4", "600_6", "700_6_6", "700_9", "900_13")


def build(source: Path) -> None:
    for weight, style in WEIGHTS.items():
        face = TTFont(source, recalcTimestamp=False)
        options = subset.Options()
        options.name_IDs = ["*"]  # Retain original copyright and OFL license metadata.
        options.name_languages = ["*"]
        subsetter = subset.Subsetter(options=options)
        subsetter.populate(unicodes=[ord(c) for c in GLYPHS])
        subsetter.subset(face)
        face = instantiateVariableFont(face, {"wght": weight}, inplace=True)
        names = {1: "OSQ Entropy", 2: style, 3: f"OSQEntropy-{style}-1.0",
                 4: f"OSQ Entropy {style}", 6: f"OSQEntropy-{style}",
                 16: "OSQ Entropy", 17: style, 18: f"OSQ Entropy {style}"}
        for name in face["name"].names:
            if name.nameID in names:
                name.string = names[name.nameID].encode(name.getEncoding())
        face.save(FONTS / f"osq_entropy_{weight}.ttf")
        face.close()
    for suffix in FACES:
        original = json.loads((FONTS / f"osq_nsc_{suffix}.json").read_text("utf-8"))
        primary = original["providers"][0]
        fallback = dict(primary, file=f"molecularmanipulator:osq_entropy_{suffix.split('_')[0]}.ttf")
        # Place before minecraft:default; referencing the old chain first would consume 熵 there.
        original["providers"].insert(1, fallback)
        (FONTS / f"osq_entropy_{suffix}.json").write_text(
            json.dumps(original, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def check() -> None:
    language = json.loads((ASSETS / "lang/zh_cn.json").read_text("utf-8"))
    text = "".join(v for k, v in language.items() if k.startswith("gui.molecularmanipulator.star_computation.")
                   or k == "block.molecularmanipulator.star_computation_engine_core")
    required = {ord(c) for c in text if "\u3400" <= c <= "\u9fff"}
    for suffix in FACES:
        chain = json.loads((FONTS / f"osq_entropy_{suffix}.json").read_text("utf-8"))["providers"]
        assert len(chain) == 3 and chain[-1] == {"type": "reference", "id": "minecraft:default"}
        assert chain[0]["file"].startswith("molecularmanipulator:osq_sc_")
        assert chain[1]["file"] == f"molecularmanipulator:osq_entropy_{suffix.split('_')[0]}.ttf"
        assert chain[0]["size"] == chain[1]["size"]
        assert chain[0]["shift"] == chain[1]["shift"]
        covered = set()
        for provider in chain[:2]:
            with TTFont(FONTS / provider["file"].split(":", 1)[1]) as face:
                cmap = face.getBestCmap()
                covered.update(cmap)
                if "osq_entropy_" in provider["file"]:
                    assert set(cmap) == {ord(c) for c in GLYPHS}, "Fallback must remain local and tiny"
                    assert face["OS/2"].usWeightClass == int(suffix.split("_")[0])
                    assert (FONTS / provider["file"].split(":", 1)[1]).stat().st_size < 16384
                    for c in GLYPHS:
                        assert face["glyf"][cmap[ord(c)]].numberOfContours > 0, "Empty glyph is not coverage"
        missing = required - covered
        assert not missing, f"{suffix}: missing {', '.join(f'U+{cp:04X}' for cp in sorted(missing))}"
        print(f"PASS osq_entropy_{suffix}: {len(required)} UI CJK code points; same-weight 熵 fallback")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path)
    args = parser.parse_args()
    if args.source:
        build(args.source)
    check()
