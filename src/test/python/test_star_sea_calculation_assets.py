"""Resource checks: python -B -m unittest discover -s src/test/python -p test_star_sea_calculation_assets.py -v"""

import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[3]
FIXTURES = ROOT / "src/test/resources/fixtures"
EXPORTER = ROOT / "src/test/python/export_star_sea_runtime.py"
spec = importlib.util.spec_from_file_location("export_star_sea_runtime", EXPORTER)
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
SOURCE = exporter.SOURCE_DIR
ASSETS = ROOT / exporter.ASSETS
MODEL_DIR = ROOT / exporter.MODEL_DIR
GLASS_UUID = "dc28d5fa-530d-5d5b-b835-db1c13427be4"
FIXTURE_HASHES = {
    "star_sea_calculation/m1-compact-tower/star-listener-m1.bbmodel": "df2b6d1070487534d63e299a6c21e465e9c2c85625490d3e8389abdfd6e7b6ff",
    "star_sea_calculation/m1-compact-tower/textures/array_atlas.png": "5c387821a7898d580aa66ce31086e38103b2c642e77aff85c42926eb28ac3c6a",
    "star_sea_calculation/m1-compact-tower/textures/array_emissive.png": "a495bc4253cb85f3b702d61339f350ecabef4a68d9d734f69bc065d7a1b6274b",
    "star_sea_calculation/m1-compact-tower/textures/body_front.png": "036c065581b556672d4ddaecb9ecdede76a8b47dd497b7b5f72322bf02898ee1",
    "star_sea_calculation/m1-compact-tower/textures/roof.png": "093121d9b15424995733079841e47a0d8db85820611067aff9fcce5c5d9a10e2",
    "star_sea_calculation/m1-compact-tower/textures/side.png": "b99ad3099635022e7d5a9df631c26d5ea2da4bd857e3e7bfdcf32cde7ac9168b",
    "star_sea_calculation/m1-controller-maze-flow/animation/array_atlas.png": "68153d3c041aafd7530504187add76c3c8c26ddef452934ec115749bc56e7e72",
    "star_sea_calculation/m1-controller-maze-flow/animation/array_emissive.png": "a551e7906eb121fc87a5072a1703440c822523e90165ae430a1ce78bc9db5efe",
    "star_sea_calculation/m1-controller-maze-flow/animation/array_atlas.png.mcmeta": "6c3653ce1b7ea1f52615b7bee05b2c0f772d0c2bbd13f107a0c40475186fb40e",
    "star_sea_calculation/m1-controller-maze-flow/animation/array_emissive.png.mcmeta": "6c3653ce1b7ea1f52615b7bee05b2c0f772d0c2bbd13f107a0c40475186fb40e",
    "star_sea_calculation/m1-white-base-port/star-listener-m1.bbmodel": "d30409b4540000bd934cddb39fca04fe166fb8b3783e0492b6bebc8a3c96e57e",
    "star_sea_calculation/m1-cabinet-uv-alignment/star-listener-m1.bbmodel": "38b6b70dbfef4f58e5fe343c4ec824b1dd4e44d165bfebdf811b816f25ed4c4e",
    "star_signal_flow/star-signal-flow.png": "6ef6b76abf7bff106dd4ec9abb776b297913b0934cf762ad5a4d93d7f72988d1",
    "star_signal_flow/animated/star-signal-flow.png": "c82465542127607d5d7cc77de7e4141a93d1506f895a2552a3cf53317e11c2ba",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def is_glass(element):
    return element["uuid"] == GLASS_UUID or "玻璃" in element["name"] or "glass" in element["name"].lower()


def native_atlas_uv(element, side, bb_point):
    """Evaluate JSON UVs at a physical point, in Minecraft's native face directions.

    This deliberately does not use the exporter's projection function or the
    concept renderer's opposite horizontal vertex order.
    """
    point = [value / 8 + shift for value, shift in zip(bb_point, (8, 0, 8))]
    low, high = element["from"], element["to"]
    axis = 2 if side in {"east", "west"} else 0
    u = (point[axis] - low[axis]) / (high[axis] - low[axis])
    if side in {"north", "east"}:
        u = 1 - u
    v = (high[1] - point[1]) / (high[1] - low[1])
    face = element["faces"][side]
    assert not face.get("rotation", 0)
    u0, v0, u1, v1 = face["uv"]
    return (u0 + u * (u1 - u0)) * 32, (v0 + v * (v1 - v0)) * 32


class StarSeaCalculationAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.archived = read_json(SOURCE / "star-listener-m1.bbmodel")
        cls.source = exporter.runtime_source(cls.archived)
        cls.models = {part: read_json(MODEL_DIR / (part + ".json")) for part in ("shell", "glass")}

    def test_retained_source_models_and_all_textures_remain_byte_identical(self):
        for relative, expected in FIXTURE_HASHES.items():
            with self.subTest(file=relative):
                self.assertEqual(expected, hashlib.sha256((FIXTURES / relative).read_bytes()).hexdigest())

    def test_every_cube_keeps_exact_mini_dimensions_and_all_six_corrected_uvs(self):
        self.assertEqual(120, len(self.source["elements"]))
        self.assertTrue({"shell.json", "glass.json"}.issubset({p.name for p in MODEL_DIR.iterdir()}))
        counts = {}
        for part, model in self.models.items():
            expected = [e for e in self.source["elements"] if is_glass(e) == (part == "glass")]
            actual = [element for element in model["elements"] if "neoforge_data" not in element]
            counts[part] = len(actual)
            self.assertEqual(len(expected), len(actual))
            self.assertNotIn("parent", model, "Each runtime part must be standalone")
            self.assertNotIn("loader", model)
            self.assertFalse(model["ambientocclusion"])
            self.assertEqual("minecraft:translucent" if part == "glass" else "minecraft:cutout", model["render_type"])
            self.assertEqual(exporter.RESOURCE + "/array_atlas", model["textures"]["atlas"])
            self.assertEqual("#atlas", model["textures"]["particle"])
            for original, mini in zip(expected, actual):
                self.assertEqual(original["name"], mini["name"])
                self.assertNotIn("rotation", mini)
                self.assertEqual([0, 0, 0], original["rotation"])
                for key in ("from", "to"):
                    for axis, shift in enumerate((8, 0, 8)):
                        self.assertAlmostEqual(original[key][axis] / 8 + shift, mini[key][axis], places=12)
                        self.assertTrue(-16 <= mini[key][axis] <= 32)
                self.assertEqual(set(original["faces"]), set(mini["faces"]))
                for side, face in mini["faces"].items():
                    port = original["faces"][side]["texture"] == 1
                    self.assertEqual("#port" if port else "#atlas", face["texture"])
                    self.assertNotIn("cullface", face, "Only one null-side quad submission is needed")
                    self.assertNotIn("tintindex", face, "The helper must tint untinted baked faces explicitly")
                    self.assertEqual(original["faces"][side].get("rotation", 0), face.get("rotation", 0))
                    for bb_uv, json_uv in zip(exporter.corrected_face_uv(original, side), face["uv"]):
                        self.assertEqual(bb_uv / (1 if port else 32), json_uv)
                        self.assertTrue(0 <= json_uv <= 16)
        self.assertEqual({"shell": 119, "glass": 1}, counts)
        cubes = [cube for model in self.models.values() for cube in model["elements"] if "neoforge_data" not in cube]
        low = [min(e["from"][i] for e in cubes) for i in range(3)]
        high = [max(e["to"][i] for e in cubes) for i in range(3)]
        self.assertEqual([3, 0, 3], low)
        self.assertEqual([13, 18, 13], high)
        self.assertEqual([5, 9, 5], [(b - a) / 16 * 8 for a, b in zip(low, high)])
        self.assertEqual(720, sum(len(cube["faces"]) for cube in cubes))

    def test_cabinet_side_border_joins_at_the_same_physical_seam_in_native_face_directions(self):
        pairs = list(zip((e for e in self.source["elements"] if not is_glass(e)),
                         (e for e in self.models["shell"]["elements"] if "neoforge_data" not in e)))
        back = next(mini for original, mini in pairs if original["name"] == "解码机柜 / 后壳")
        changed = 0
        for original, mini in pairs:
            for side, face in mini["faces"].items():
                if original["faces"][side]["texture"] == 1:
                    continue
                if face["uv"] != [v / 32 for v in original["faces"][side]["uv"]]:
                    self.assertIn(original["name"], exporter.CABINET_SHELL_PARTS | {"内嵌 ME 接口面板"})
                    changed += 1
        self.assertEqual(31, changed, "Only the cabinet casing and two inner panel UVs differ from archived M1")
        for side, bound, edge in (("east", "to", 22.4), ("west", "from", -22.4)):
            strips = [(original, mini) for original, mini in pairs
                      if original["name"] in exporter.CABINET_FRONT_PARTS and original[bound][0] == edge]
            self.assertEqual(3, len(strips))
            for original, mini in strips:
                for y in (original["from"][1], (original["from"][1] + original["to"][1]) / 2, original["to"][1]):
                    point = (edge, y, -18.56)
                    for a, b in zip(native_atlas_uv(back, side, point), native_atlas_uv(mini, side, point)):
                        self.assertAlmostEqual(a, b, places=10,
                                               msg="Both faces sample the same texel at the ACTUAL front/rear joint")
        front = [(original, mini) for original, mini in pairs if original["name"] in exporter.CABINET_FRONT_PARTS]
        for original, mini in front:
            for x in (original["from"][0], original["to"][0]):
                for y in (original["from"][1], original["to"][1]):
                    u, v = native_atlas_uv(mini, "north", (x, y, -22.4))
                    self.assertAlmostEqual(208.5 + (22.4 - x) / 44.8 * 47, u, places=10)
                    self.assertAlmostEqual(272.5 + (35.2 - y) / 25.6 * 31, v, places=10)
            for side, face in mini["faces"].items():
                if original["faces"][side]["uv"] == exporter.DECORATED_SHELL_UV:
                    self.assertNotEqual([v / 32 for v in exporter.DECORATED_SHELL_UV], face["uv"])

    def test_cabinet_white_border_stays_at_outer_edges_not_internal_joints_in_all_32_frames(self):
        pairs = list(zip((e for e in self.source["elements"] if not is_glass(e)),
                         (e for e in self.models["shell"]["elements"] if "neoforge_data" not in e)))
        white = (199, 206, 208, 255)
        samples = []
        for original, mini in pairs:
            if original["name"] == "接口侧立框":
                sign = 1 if original["from"][0] > 0 else -1
                samples.extend([(mini, "north", (sign * 22.39, 20.4, -22.4), True),
                                (mini, "north", (sign * 18.3, 20.4, -22.4), False)])
            if original["name"] == "解码机柜 / 后壳":
                for side, x in (("east", 22.4), ("west", -22.4)):
                    samples.extend([(mini, side, (x, 20.4, 22.39), True),
                                    (mini, side, (x, 20.4, -18.55), False)])
        self.assertEqual(8, len(samples))
        with Image.open(ROOT / exporter.TEXTURE_DIR / "array_atlas.png") as atlas:
            for frame in range(32):
                for mini, side, point, is_border in samples:
                    u, v = native_atlas_uv(mini, side, point)
                    color = atlas.getpixel((math.floor(u), math.floor(v) + frame * 512))
                    self.assertEqual(is_border, color == white,
                                     msg=f"Frame {frame}, {side} {point}: white belongs only at the cabinet outline")

    def test_me_port_circuits_do_not_paint_a_second_white_frame_inside_the_real_bezel(self):
        panels = [e for e in self.models["shell"]["elements"] if e["name"] == "内嵌 ME 接口面板"]
        self.assertEqual(2, len(panels))
        with Image.open(ROOT / exporter.TEXTURE_DIR / "array_atlas.png") as atlas:
            for panel in panels:
                uv = [v * 32 for v in panel["faces"]["north"]["uv"]]
                # Sample only texels 2..13 of each 16-pixel material island.
                self.assertEqual([2.5, 464 + 2.5, 13.5, 464 + 13.5],
                                 [uv[0] % 24 - 8, uv[1], uv[2] % 24 - 8, uv[3]])
                for frame in range(32):
                    crop = atlas.crop((math.floor(uv[0]), math.floor(uv[1]) + frame * 512,
                                       math.ceil(uv[2]), math.ceil(uv[3]) + frame * 512))
                    self.assertNotIn((199, 206, 208, 255), set(crop.getdata()), "Only the real bezel is shell white")

    def test_glass_material_uses_uuid_or_name_instead_of_atlas_index(self):
        self.assertEqual({0, 1}, {face["texture"] for e in self.source["elements"] for face in e["faces"].values()})
        glass = next(e for e in self.source["elements"] if e["uuid"] == GLASS_UUID)
        self.assertEqual("glass", exporter.material({**glass, "name": "renamed inspection panel"}))
        self.assertEqual("glass", exporter.material({**glass, "uuid": "renamed-id"}))
        self.assertEqual("glass", exporter.material({"uuid": "new-id", "name": "GLASS inspection panel"}))
        self.assertEqual("shell", exporter.material({"uuid": "new-id", "name": "inspection frame"}))

    def test_runtime_animation_matches_approved_maze_flow_and_keeps_original_collars(self):
        texture_dir = ROOT / exporter.TEXTURE_DIR
        names = {"array_atlas.png", "array_emissive.png", "array_atlas.png.mcmeta", "array_emissive.png.mcmeta", "me_connection_port.png"}
        self.assertEqual(names, {p.name for p in texture_dir.iterdir()})
        approved = exporter.FLOW_DIR
        self.assertEqual((approved / "array_atlas.png").read_bytes(), (texture_dir / "array_atlas.png").read_bytes())
        for name in ("array_atlas.png", "array_emissive.png"):
            meta = read_json(texture_dir / (name + ".mcmeta"))
            self.assertEqual({"animation": {"width": 512, "height": 512, "frametime": 2, "interpolate": False}}, meta)
            self.assertEqual((approved / (name + ".mcmeta")).read_bytes(), (texture_dir / (name + ".mcmeta")).read_bytes())
            with Image.open(texture_dir / name) as image, Image.open(SOURCE / "textures" / name) as original:
                self.assertEqual((512, 512 * 32), image.size)
                hashes = set()
                for frame in range(32):
                    picture = image.crop((0, frame * 512, 512, (frame + 1) * 512))
                    hashes.add(hashlib.sha256(picture.tobytes()).digest())
                    for box in ((64, 328, 128, 376), (208, 240, 336, 252)):
                        # Compare RGB; emission transparency is intentionally changed for the overlay.
                        self.assertEqual(original.crop(box).convert("RGB").tobytes(),
                                         picture.crop(box).convert("RGB").tobytes())
                self.assertEqual(32, len(hashes), name)

    def test_emissive_overlay_is_transparent_outside_authored_lights(self):
        texture_dir = ROOT / exporter.TEXTURE_DIR
        with Image.open(exporter.FLOW_DIR / "array_emissive.png") as original, Image.open(texture_dir / "array_emissive.png") as runtime:
            self.assertEqual(original.convert("RGB").tobytes(), runtime.convert("RGB").tobytes())
            expected_alpha = original.convert("L").point(lambda value: 255 if value else 0)
            self.assertEqual(expected_alpha.tobytes(), runtime.getchannel("A").tobytes())
            alpha = runtime.getchannel("A")
            self.assertEqual({0, 255}, set(alpha.getdata()))
            self.assertLess(alpha.histogram()[255], runtime.width * runtime.height // 10)

    def test_only_light_bearing_faces_get_offset_full_bright_overlays(self):
        shell = self.models["shell"]
        self.assertEqual(exporter.RESOURCE + "/array_emissive", shell["textures"]["emissive"])
        originals = {(cube["name"], tuple(cube["from"])): cube for cube in shell["elements"] if "neoforge_data" not in cube}
        glow = [cube for cube in shell["elements"] if "neoforge_data" in cube]
        self.assertTrue(glow)
        self.assertLess(len(glow), len(originals), "Only selected source surfaces need overlay geometry")
        with Image.open(ROOT / exporter.TEXTURE_DIR / "array_emissive.png") as image:
            mask = Image.new("L", (512, 512))
            for frame in range(32):
                mask = ImageChops.lighter(mask, image.crop((0, frame * 512, 512, (frame + 1) * 512)).getchannel("A"))
        for element in glow:
            self.assertEqual(1, len(element["faces"]), "One normal-only offset per luminous face")
            side = next(iter(element["faces"]))
            axis, direction = {"west": (0, -1), "east": (0, 1), "down": (1, -1),
                               "up": (1, 1), "north": (2, -1), "south": (2, 1)}[side]
            offset = [exporter.GLOW_OFFSET * direction if i == axis else 0 for i in range(3)]
            key = (element["name"].removesuffix(" / 流光 / " + side),
                   tuple(round(value - delta, 8) for value, delta in zip(element["from"], offset)))
            base = originals[key]
            self.assertFalse(element["shade"])
            self.assertEqual({"block_light": 15, "sky_light": 15, "ambient_occlusion": False}, element["neoforge_data"])
            for key in ("from", "to"):
                for i in range(3):
                    self.assertAlmostEqual(base[key][i] + offset[i], element[key][i])
                    if i != axis:
                        self.assertEqual(base[key][i], element[key][i], "No in-plane expansion or overlapping seams")
            for side, face in element["faces"].items():
                self.assertEqual("#emissive", face["texture"])
                self.assertEqual(base["faces"][side]["uv"], face["uv"])
                self.assertNotIn("cullface", face)
                u0, v0, u1, v1 = (v * 32 for v in face["uv"])
                self.assertIsNotNone(mask.crop((math.floor(min(u0, u1)), math.floor(min(v0, v1)),
                                               math.ceil(max(u0, u1)), math.ceil(max(v0, v1)))).getbbox())
        self.assertFalse(any("neoforge_data" in cube for cube in self.models["glass"]["elements"]))

    def test_white_connection_socket_is_static_square_and_matches_the_bottom_front_core(self):
        port = next(e for e in self.source["elements"] if e["name"] == exporter.PORT_NAME)
        self.assertEqual([-8, 0, -40], port["from"])
        self.assertEqual([8, 16, -36], port["to"])
        bounds = [v / 16 + shift for key in ("from", "to")
                  for v, shift in zip(port[key], (.5, 0, 2.5))]
        self.assertEqual([0, 0, 0, 1, 1, .25], bounds)
        runtime = next(e for e in self.models["shell"]["elements"] if e["name"] == exporter.PORT_NAME)
        self.assertEqual("#port", runtime["faces"]["north"]["texture"])
        self.assertEqual([0, 0, 16, 16], runtime["faces"]["north"]["uv"])
        self.assertEqual(exporter.RESOURCE + "/me_connection_port", self.models["shell"]["textures"]["port"])
        self.assertNotIn("neoforge_data", runtime)
        for e in self.source["elements"]:
            if e is not port:
                self.assertFalse(all(max(e["from"][i], port["from"][i]) < min(e["to"][i], port["to"][i])
                                     for i in range(3)), "No overlapping volume or coplanar outer base skins")
        with Image.open(ROOT / exporter.TEXTURE_DIR / "me_connection_port.png") as image:
            self.assertEqual((16, 16), image.size)
            self.assertEqual(exporter.connection_port().tobytes(), image.tobytes())
            for y in range(16):
                for x in range(16):
                    if x < 2 or x >= 14 or y < 2 or y >= 14:
                        self.assertEqual((199, 206, 208, 255), image.getpixel((x, y)), "Solid white surrounding casing")
            self.assertEqual((138, 217, 200, 255), image.getpixel((7, 7)))

    def test_item_composite_draws_both_children_once_with_legal_transforms(self):
        item = read_json(ASSETS / "models/item/star_sea_calculation_mainframe.json")
        self.assertEqual("neoforge:composite", item["loader"])
        self.assertEqual(["shell", "glass"], item["item_render_order"])
        self.assertEqual({"shell", "glass"}, set(item["children"]))
        self.assertNotIn("elements", item)
        for part, child in item["children"].items():
            self.assertEqual({"parent": exporter.RESOURCE + "/" + part}, child)
        self.assertEqual({"gui", "ground", "fixed", "head", "thirdperson_righthand", "thirdperson_lefthand",
                          "firstperson_righthand", "firstperson_lefthand"}, set(item["display"]))
        for transform in item["display"].values():
            for key, limit in (("rotation", 360), ("translation", 80), ("scale", 4)):
                self.assertEqual(3, len(transform[key]))
                self.assertTrue(all(math.isfinite(v) and abs(v) <= limit for v in transform[key]))
            self.assertTrue(all(v > 0 for v in transform["scale"]))

    def test_generated_geometry_has_120_port_adjusted_cuboids_and_no_cell_literals(self):
        java = (ROOT / exporter.GEOMETRY_JAVA).read_text(encoding="utf-8")
        table = java.split("double[][] CUBOIDS = {", 1)[1].split("    };", 1)[0]
        rows = [[float(v) for v in row.split(",")] for row in re.findall(r"\{([^{}]+)\}", table)]
        self.assertEqual(120, len(rows))
        for original, actual in zip(self.source["elements"], rows):
            expected = [v / 16 + shift for key in ("from", "to") for v, shift in zip(original[key], (.5, 0, 2.5))]
            for a, b in zip(expected, actual):
                self.assertAlmostEqual(a, b, places=12)
        occupied = 0
        for y in range(9):
            for z in range(5):
                for x in range(-2, 3):
                    occupied += any(all(max(row[i], n) < min(row[i + 3], n + 1)
                                        for i, n in enumerate((x, y, z))) for row in rows)
        self.assertEqual(149, occupied)
        self.assertEqual(76, 225 - occupied)

    def test_exporter_rejects_new_rotations_in_elements_and_groups(self):
        for kind in ("cube", "group"):
            source = copy.deepcopy(self.archived)
            node = source["elements"][0] if kind == "cube" else source["outliner"][0]
            node["rotation"] = [0, 90, 0]
            with self.subTest(kind=kind), self.assertRaisesRegex(ValueError, "Rotated source node"):
                exporter.validate_source(source)

    def test_clean_export_reproduces_all_nine_generated_files_and_check_is_read_only(self):
        with tempfile.TemporaryDirectory(prefix="star-sea-export-test-") as directory:
            output = Path(directory)
            command = [sys.executable, "-B", str(EXPORTER), "--output-root", str(output)]
            subprocess.run(command, check=True, capture_output=True)
            generated = exporter.generated_files()
            self.assertEqual(9, len(generated))
            self.assertEqual(set(generated), {p.relative_to(output) for p in output.rglob("*") if p.is_file()})
            for relative in generated:
                self.assertEqual((ROOT / relative).read_bytes(), (output / relative).read_bytes())
            before = {p: p.stat().st_mtime_ns for p in output.rglob("*") if p.is_file()}
            subprocess.run(command + ["--check"], check=True, capture_output=True)
            self.assertEqual(before, {p: p.stat().st_mtime_ns for p in before})
            target = output / exporter.MODEL_DIR / "shell.json"
            target.write_text("{}", encoding="utf-8")
            result = subprocess.run(command + ["--check"], capture_output=True)
            self.assertEqual(1, result.returncode)
            self.assertEqual("{}", target.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
