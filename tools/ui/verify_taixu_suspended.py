"""Verify the suspended crystal patch scope against the installed-release baseline."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument("baseline", type=Path)
parser.add_argument("patch", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
root = "com/atir/molecularmanipulator/"
classes = ["blockentity/TaixuStructure", "blockentity/TaixuBlockEntity", "blockentity/TaixuControllerEmbedding", "blockentity/TaixuSuspendedUpgrade",
           "blockentity/TaixuMotionGeometry", "blockentity/TaixuMotionState", "entity/TaixuAssemblyEntity",
           "client/TaixuRenderer", "client/TaixuAssemblyRenderer", "client/render/TaixuStructureEffects", "client/TaixuScreen", "client/TaixuGhostPreview", "menu/TaixuMenu"]
prefixes = [root + name for name in classes]
languages = {f"assets/molecularmanipulator/lang/{lang}.json" for lang in ("zh_cn", "en_us")}

def allowed_class(name):
    return any(name == prefix + ".class" or name.startswith(prefix + "$") and name.endswith(".class") for prefix in prefixes)

with ZipFile(args.baseline) as base, ZipFile(args.patch) as patch:
    base_names, patch_names = set(base.namelist()), set(patch.namelist())
    assert len(patch.namelist()) == len(patch_names), "Duplicate archive entries"
    assert not any("GameTests" in name or "/verification/" in name or name.startswith("tools/") for name in patch_names), "Verification sources in release"
    changed = []
    for name in sorted(base_names | patch_names):
        if name in base_names and name in patch_names and base.read(name) == patch.read(name):
            continue
        assert allowed_class(name) or name in languages, f"Unrelated release content changed: {name}"
        changed.append(name)
    assert root + "blockentity/TaixuControllerEmbedding.class" in patch_names
    language_changes = {}
    for name in languages:
        old, new = json.loads(base.read(name)), json.loads(patch.read(name))
        keys = sorted(key for key in old.keys() | new.keys() if old.get(key) != new.get(key))
        assert all(key.startswith("gui.molecularmanipulator.taixu.upgrade") or key == "gui.molecularmanipulator.taixu.height_range" for key in keys), "Unrelated translations changed"
        assert new["gui.molecularmanipulator.taixu.height_range"].count("%s") == 3
        language_changes[name] = keys

result = {
    "baseline": str(args.baseline.resolve()), "patch": str(args.patch.resolve()),
    "baselineSha256": hashlib.sha256(args.baseline.read_bytes()).hexdigest(),
    "patchSha256": hashlib.sha256(args.patch.read_bytes()).hexdigest(),
    "changedEntries": changed, "languageChanges": language_changes,
    "unrelatedContentPreserved": True, "verificationClassesAbsent": True,
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("TAIXU_SUSPENDED_PATCH_PASS changedEntries=" + str(len(changed)) + " unrelatedContentPreserved=true verificationClassesAbsent=true")
